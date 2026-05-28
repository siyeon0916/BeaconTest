package com.example.beaconscanner

object BeaconConfig {

    data class BeaconInfo(
        val mac: String,
        val x: Double,      // 미터 단위 (강의실 왼쪽 벽 = 0)
        val y: Double,      // 미터 단위 (강의실 위쪽 벽 = 0)
        val txPower: Int = -65  // 1m 거리에서 실측한 RSSI 평균값으로 교체
    )

    val BEACONS = listOf(
        BeaconInfo("AC:23:3F:F6:AE:B9", x = 0.5,  y = 0.5 ),  // 강의실 좌상단
        BeaconInfo("AC:23:3F:F6:AE:B6", x = 11.5, y = 0.5 ),  // 강의실 우상단
        BeaconInfo("AC:23:3F:F6:AE:BE", x = 6.0,  y = 4.0 ),  // 강의실 중앙
        BeaconInfo("AC:23:3F:F6:AE:BF", x = 0.5,  y = 7.5 ),  // 강의실 좌하단
        BeaconInfo("AC:23:3F:F6:AE:B5", x = 11.5, y = 7.5 ),  // 강의실 우하단
    )

    // Path-loss 지수: 자유공간 2.0 / 실내 일반 2.5~3.0 / 장애물 많은 실내 3.0~3.5
    // → 강의실에서 실측 후 조정 (2.5로 시작 권장)
    const val PATH_LOSS_N = 2.5

    // 공간 실측값 (강의실 실제 크기로 교체)
    const val ROOM_WIDTH  = 12.0   // 미터
    const val ROOM_HEIGHT = 8.0    // 미터

    // 그리드 셀 크기 — 런타임에 변경해서 비교 테스트 가능
    var GRID_CELL_SIZE = 1.0       // 미터
}