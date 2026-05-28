package com.example.beaconscanner

import kotlin.math.pow

object LocationEstimator {

    data class Position(val x: Double, val y: Double)

    data class GridCell(val col: Int, val row: Int) {
        override fun toString() = "col=$col, row=$row"
    }

    /**
     * Path-loss 공식: d = 10 ^ ((txPower - RSSI) / (10 * n))
     * RSSI 0이면 신호 없음 → MAX_VALUE 반환
     */
    fun rssiToDistance(rssi: Int, txPower: Int = -65): Double {
        if (rssi == 0) return Double.MAX_VALUE
        val exponent = (txPower - rssi) / (10.0 * BeaconConfig.PATH_LOSS_N)
        return 10.0.pow(exponent).coerceIn(0.1, 30.0)   // 0.1m~30m 범위로 클램프
    }

    /**
     * Weighted Centroid 위치 추정
     * - 각 비콘 가중치 = 1 / d²  (가까울수록 가중치 ↑)
     * - 비콘 3개 미만 식별 시 null 반환
     *
     * beaconDistances: MAC 주소 → 추정 거리(m) 맵
     */
    fun estimatePosition(beaconDistances: Map<String, Double>): Position? {
        val matched = BeaconConfig.BEACONS.filter { it.mac in beaconDistances }
        if (matched.size < 3) return null

        var wx = 0.0;  var wy = 0.0;  var wsum = 0.0

        for (info in matched) {
            val d = beaconDistances[info.mac] ?: continue
            val w = 1.0 / (d * d).coerceAtLeast(0.0001)
            wx   += info.x * w
            wy   += info.y * w
            wsum += w
        }

        if (wsum == 0.0) return null
        return Position(
            x = (wx / wsum).coerceIn(0.0, BeaconConfig.ROOM_WIDTH),
            y = (wy / wsum).coerceIn(0.0, BeaconConfig.ROOM_HEIGHT)
        )
    }

    /**
     * 실수 좌표 → 그리드 셀 인덱스
     */
    fun toGridCell(
        pos: Position,
        cellSize: Double = BeaconConfig.GRID_CELL_SIZE
    ): GridCell {
        val maxCol = (BeaconConfig.ROOM_WIDTH  / cellSize).toInt() - 1
        val maxRow = (BeaconConfig.ROOM_HEIGHT / cellSize).toInt() - 1
        return GridCell(
            col = (pos.x / cellSize).toInt().coerceIn(0, maxCol),
            row = (pos.y / cellSize).toInt().coerceIn(0, maxRow)
        )
    }
}