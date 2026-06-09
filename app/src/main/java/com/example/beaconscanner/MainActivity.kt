package com.example.beaconscanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import okhttp3.Call
import org.altbeacon.beacon.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var beaconManager: BeaconManager
    private lateinit var adapter: BeaconAdapter

    // 비콘 캐시
    private val beaconCache = mutableMapOf<String, CachedBeacon>()

    private var lastSentPosition: LocationEstimator.Position? = null

    // 화면 갱신용 Handler
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 500L  // 0.5초마다 화면 갱신

    // 비콘이 이 시간(ms) 동안 안 잡히면 목록에서 제거
    private val beaconTimeoutMs = 4000L

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

    // 캐시 항목
    data class CachedBeacon(
        val beacon: Beacon,
        val lastSeenMs: Long,
        val rssiHistory: ArrayDeque<Int> = ArrayDeque(5) // 최근 5개 RSSI
    )

    // 화면 갱신 루프
    private val refreshRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            // 오래된 비콘 제거
            val expired = beaconCache.entries.filter { now - it.value.lastSeenMs > beaconTimeoutMs }
            expired.forEach { beaconCache.remove(it.key) }

            // UI 갱신
            updateUI()
            val distances = beaconCache.values.mapNotNull { cached ->
                val key = beaconKey(cached.beacon)
                val configInfo = BeaconConfig.BEACONS.find { it.key == key } ?: return@mapNotNull null
                val avgRssi = cached.rssiHistory.average().toInt()
                val dist = LocationEstimator.rssiToDistance(avgRssi, configInfo.txPower)
                key to dist
                }
                .toMap()
            val position = LocationEstimator.estimatePosition(distances)
            if (position != null) {
                val last = lastSentPosition
                val moved = last == null ||
                        Math.hypot(position.x - last.x, position.y - last.y) > 0.3 // 30cm 이상 이동 시만 전송
                if (moved) {
                    sendToServer(position.x, position.y )
                    lastSentPosition = position
                }
                tvLocation.text = "추정 위치 : X=%.2f m, Y = %.2f m".format(position.x, position.y)
            } else {
                tvLocation.text = "위치 추정 중 ... (등록 비콘 감지 필요)"
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

        // 스캔 주기 설정
        beaconManager.foregroundScanPeriod = 2000L       // 2초 스캔
        beaconManager.foregroundBetweenScanPeriod = 0L  // 연속 스캔

        btnScan.setOnClickListener { checkPermissionsAndScan() }
        btnStop.setOnClickListener { stopScan() }

        val region = Region("all-beacons", null, null, null)
        val regionViewModel = beaconManager.getRegionViewModel(region)
        regionViewModel.rangedBeacons.observe(this) { beacons ->
            val now = System.currentTimeMillis()
            for (beacon in beacons) {
                val key = beaconKey(beacon)
                val existing = beaconCache[key]
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

        checkBluetooth()
    }

    private fun checkBluetooth() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (btManager.adapter == null || !btManager.adapter.isEnabled) {
            Toast.makeText(this, "블루투스를 켜주세요", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
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
        beaconManager.startRangingBeacons(Region("all-beacons", null, null, null))
        btnScan.isEnabled = false
        btnStop.isEnabled = true
        tvStatus.text = "● 스캔 중"
        tvStatus.setTextColor(Color.parseColor("#22C55E"))
        tvEmpty.text = "스캔 중... 비콘을 탐색하고 있습니다"

        // 갱신 시작
        handler.post(refreshRunnable)
    }

    private fun stopScan() {
        beaconManager.stopRangingBeacons(Region("all-beacons", null, null, null))
        if (beaconManager.isBound(this)) {
            beaconManager.unbind(this)
        }
        handler.removeCallbacks(refreshRunnable)
        btnScan.isEnabled = true
        btnStop.isEnabled = false
        tvStatus.text = "● 대기중"
        tvStatus.setTextColor(Color.parseColor("#6B7280"))
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
            View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
        beaconManager.stopRangingBeacons(Region("all-beacons", null, null, null))
    }
    private val httpClient = OkHttpClient()
    private fun sendToServer(x: Double, y: Double) {
        val json = """{"x":$x,"y":$y}"""
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BeaconConfig.SERVER_URL}/location")
            .post(body)
            .build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }
    private fun beaconKey(beacon: Beacon): String {
        val uuid = beacon.id1?.toString()?.uppercase() ?: "UNKNOWN"
        val major = beacon.id2?.toInt() ?: 0
        val minor = beacon.id3?.toInt() ?: 0
        return "$uuid-$major-$minor"
    }
}
