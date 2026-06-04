package com.example.beaconscanner

import com.example.beaconscanner.BeaconConfig
import org.altbeacon.beacon.BuildConfig

object BeaconConfig {

    data class BeaconInfo(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val x: Double,      // 미터 단위 (강의실 왼쪽 벽 = 0)
        val y: Double,      // 미터 단위 (강의실 위쪽 벽 = 0)
        val txPower: Int = -65  // 1m 거리 실측 RSSI
    ) {
        val key: String get() = "${uuid.uppercase()}-$major-$minor"
    }

    val BEACONS = listOf(
        BeaconInfo("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", major = 40011, minor = 29433, x = 0.5,  y = 0.5 ),  // 강의실 좌상단(AEB9)
        BeaconInfo("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", major = 40011, minor = 29445, x = 11.5, y = 0.5 ),  // 강의실 우상단(AEC5)
        BeaconInfo("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", major = 40011, minor = 29430, x = 6.0,  y = 4.0 ),  // 강의실 중앙(AEB6)
        BeaconInfo("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", major = 40011, minor = 29429, x = 0.5,  y = 7.5 ),  // 강의실 좌하단(AEB5)
        BeaconInfo("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", major = 40011, minor = 29427, x = 11.5, y = 7.5 ),  // 강의실 우하단(AEB3)
    )

    // 경로 손실 지수(실내 기본값 2.5)
    const val PATH_LOSS_N = 2.5

    // 강의실 크기
    const val ROOM_WIDTH  = 12.0   // 미터
    const val ROOM_HEIGHT = 8.0    // 미터

    // 그리드 셀 크기
    var GRID_CELL_SIZE = 1.0       // 미터

    val SERVER_URL = "http://${BuildConfig.SERVER_IP}:3000"
}