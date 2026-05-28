package com.example.beaconscanner

import android.graphics.Color

object BeaconUtils {
    fun rssiColor(rssi: Int): Int = when {
        rssi >= -60 -> Color.parseColor("#4ADE80")
        rssi >= -70 -> Color.parseColor("#A3E635")
        rssi >= -80 -> Color.parseColor("#FBBF24")
        rssi >= -90 -> Color.parseColor("#FB923C")
        else        -> Color.parseColor("#F87171")
    }

    fun rssiLabel(rssi: Int): String = when {
        rssi >= -60 -> "매우 강함"
        rssi >= -70 -> "강함"
        rssi >= -80 -> "보통"
        rssi >= -90 -> "약함"
        else        -> "매우 약함"
    }
}