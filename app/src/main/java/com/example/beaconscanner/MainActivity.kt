package com.example.beaconscanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.altbeacon.beacon.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private lateinit var adapter: BeaconAdapter

    // 비콘 캐시: MAC 주소를 키로 마지막으로 받은 비콘 저장
    private val beaconCache = mutableMapOf<String, CachedBeacon>()

    // 화면 갱신용 Handler
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 500L  // 0.5초마다 화면 갱신

    // 비콘이 이 시간(ms) 동안 안 잡히면 목록에서 제거
    private val beaconTimeoutMs = 8000L

    private lateinit var btnScan: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvStrong: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvLocation: TextView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // 비콘 캐시 데이터 클래스
    data class CachedBeacon(
        val beacon: Beacon,
        val lastSeenMs: Long,
        val rssiHistory: ArrayDeque<Int> = ArrayDeque(5) // 최근 5개 RSSI 저장
    )

    // 화면 주기적 갱신 Runnable
    private val refreshRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            // 타임아웃된 비콘 제거
            val expired = beaconCache.entries.filter { now - it.value.lastSeenMs > beaconTimeoutMs }
            expired.forEach { beaconCache.remove(it.key) }

            // 화면 업데이트
            updateUI()
            val distances = beaconCache.values.associate { cached ->
                val avgRssi = cached.rssiHistory.average().toInt()
                cached.beacon.bluetoothAddress to LocationEstimator.rssiToDistance(avgRssi)
            }
            val position = LocationEstimator.estimatePosition(distances)
            if (position != null) {
                val rssiMap = beaconCache.values.associate { cached ->
                    cached.beacon.bluetoothAddress to cached.rssiHistory.average().toInt()
                }
                sendToServer(position.x, position.y)
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScan  = findViewById(R.id.btnScan)
        btnStop  = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvCount  = findViewById(R.id.tvCount)
        tvStrong = findViewById(R.id.tvStrong)
        tvEmpty  = findViewById(R.id.tvEmpty)
        tvLocation = findViewById(R.id.tvLocation)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = BeaconAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        beaconManager = BeaconManager.getInstanceForApplication(this)

        // 스캔 주기 최적화
        beaconManager.foregroundScanPeriod = 2000L       // 스캔 시간 2.0초
        beaconManager.foregroundBetweenScanPeriod = 0L  // 스캔 사이 대기 0초 (연속 스캔)

        btnScan.setOnClickListener { checkPermissionsAndScan() }
        btnStop.setOnClickListener { stopScan() }

        checkBluetooth()
    }

    private fun checkBluetooth() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter == null || !btManager.adapter.isEnabled) {
            Toast.makeText(this, "블루투스를 켜주세요", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startScan()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) startScan()
        else Toast.makeText(this, "블루투스/위치 권한이 필요합니다", Toast.LENGTH_LONG).show()
    }

    private fun startScan() {
        beaconManager.bind(this)
        btnScan.isEnabled = false
        btnStop.isEnabled = true
        tvStatus.text = "● 스캔 중"
        tvStatus.setTextColor(Color.parseColor("#22C55E"))
        tvEmpty.text = "스캔 중... 비콘을 탐색하고 있습니다"

        // 화면 주기적 갱신 시작
        handler.post(refreshRunnable)
    }

    private fun stopScan() {
        beaconManager.unbind(this)
        handler.removeCallbacks(refreshRunnable)
        btnScan.isEnabled = true
        btnStop.isEnabled = false
        tvStatus.text = "● 대기중"
        tvStatus.setTextColor(Color.parseColor("#6B7280"))
    }

    // 함수 시그니처 원래대로
    override fun onBeaconServiceConnect() {
        beaconManager.removeAllRangeNotifiers()
        beaconManager.addRangeNotifier { beacons, _ ->
            val now = System.currentTimeMillis()
            for (beacon in beacons) {
                val key = beacon.bluetoothAddress
                val existing = beaconCache[key]

                // RSSI 히스토리 유지 (평균 계산용)
                val history = existing?.rssiHistory ?: ArrayDeque(5)
                if (history.size >= 5) history.removeFirst()
                history.addLast(beacon.rssi)

                beaconCache[key] = CachedBeacon(
                    beacon = beacon,
                    lastSeenMs = now,
                    rssiHistory = history
                )
            }
        }

        try {
            beaconManager.startRangingBeaconsInRegion(
                Region("all-beacons", null, null, null)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateUI() {
        val sortedBeacons = beaconCache.values
            .sortedByDescending { it.rssiHistory.average() }
            .map { it.beacon }
        adapter.updateBeacons(sortedBeacons, beaconCache)

        val strong = beaconCache.values.count { it.rssiHistory.average() >= -70 }
        tvCount.text = "${beaconCache.size}"
        tvStrong.text = "$strong"
        tvEmpty.visibility = if (beaconCache.isEmpty())
            android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
        if (beaconManager.isBound(this)) beaconManager.unbind(this)
    }
    private fun sendToServer(x: Double, y: Double) {
        val client = OkHttpClient()
        val json = """{"x":$x,"y":$y}"""
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://192.168.0.3:3000/location")
            .post(body)
            .build()
        Thread {
            try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
