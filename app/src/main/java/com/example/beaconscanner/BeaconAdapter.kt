package com.example.beaconscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.altbeacon.beacon.Beacon

class BeaconAdapter(private val beacons: MutableList<Beacon>) :
    RecyclerView.Adapter<BeaconAdapter.ViewHolder>() {

    // MainActivity의 캐시 참조 (RSSI 평균, lastSeen 접근용)
    private var cache: Map<String, MainActivity.CachedBeacon> = emptyMap()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvId: TextView = view.findViewById(R.id.tvId)
        val tvRssi: TextView = view.findViewById(R.id.tvRssi)
        val tvSignalLabel: TextView = view.findViewById(R.id.tvSignalLabel)
        val tvDistance: TextView = view.findViewById(R.id.tvDistance)
        val tvTxPower: TextView = view.findViewById(R.id.tvTxPower)
        val tvMac: TextView = view.findViewById(R.id.tvMac)
        val tvType: TextView = view.findViewById(R.id.tvType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_beacon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val beacon = beacons[position]
        val cached = cache[beacon.bluetoothAddress]

        // RSSI: 최근 5회 평균값 사용 (안정적)
        val avgRssi = cached?.rssiHistory?.average()?.toInt() ?: beacon.rssi
        val rawRssi = beacon.rssi

        // 이름
        val name = beacon.bluetoothName?.takeIf { it.isNotBlank() } ?: "(이름 없음)"
        holder.tvName.text = name

        // ID
        val id1 = beacon.id1?.toString()
        val id2 = beacon.id2?.toString()
        val id3 = beacon.id3?.toString()
        val idText = when {
            id1 != null && id2 != null && id3 != null -> "UUID: $id1\nMajor: $id2  Minor: $id3"
            id1 != null -> "ID: $id1"
            else -> "MAC: ${beacon.bluetoothAddress}"
        }
        holder.tvId.text = idText

        // RSSI 표시 (평균값)
        holder.tvRssi.text = "$avgRssi dBm"
        holder.tvRssi.setTextColor(BeaconUtils.rssiColor(avgRssi))
        holder.tvSignalLabel.text = "${BeaconUtils.rssiLabel(avgRssi)} (현재: $rawRssi)"
        holder.tvSignalLabel.setTextColor(BeaconUtils.rssiColor(avgRssi))

        // 거리 (평균 RSSI 기반으로 계산)
        val dist = beacon.distance
        holder.tvDistance.text = "거리: ${"%.2f".format(dist)} m"
        holder.tvTxPower.text = "TX: ${beacon.txPower} dBm"
        holder.tvMac.text = "MAC: ${beacon.bluetoothAddress}"

        // 비콘 타입
        val type = when {
            beacon.serviceUuid == 0xFEAA                -> "Eddystone"
            id1 != null && id2 != null && id3 != null   -> "iBeacon"
            id1 != null && id2 != null                  -> "AltBeacon"
            id1 != null                                 -> "AltBeacon"
            else                                        -> "BLE Device"
        }
        holder.tvType.text = type
    }

    override fun getItemCount() = beacons.size

    fun updateBeacons(newBeacons: List<Beacon>, newCache: Map<String, MainActivity.CachedBeacon>) {
        cache = newCache
        beacons.clear()
        beacons.addAll(newBeacons)
        notifyDataSetChanged()
    }
}
