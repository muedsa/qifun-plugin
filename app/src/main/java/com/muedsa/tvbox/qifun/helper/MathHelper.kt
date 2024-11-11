package com.muedsa.tvbox.qifun.helper

import kotlin.math.abs
import kotlin.math.max

object MathHelper {

    fun dtwDistance(projection1: List<Int>, projection2: List<Int>): Double {
        val n = projection1.size
        val m = projection2.size
        val dtwMatrix = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) {
            for (j in 0..m) {
                dtwMatrix[i][j] = if (i == 0 && j == 0) 0.0 else Double.MAX_VALUE
            }
        }
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = abs(projection1[i - 1].toDouble() - projection2[j - 1].toDouble())
                dtwMatrix[i][j] = cost + minOf(
                    dtwMatrix[i - 1][j],
                    dtwMatrix[i][j - 1],
                    dtwMatrix[i - 1][j - 1]
                )
            }
        }
        return dtwMatrix[n][m]
    }

    fun lcsLength(projection1: List<Int>, projection2: List<Int>): Int {
        val n = projection1.size
        val m = projection2.size
        val lcsMatrix = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) {
            for (j in 0..m) {
                if (i == 0 || j == 0) {
                    lcsMatrix[i][j] = 0
                } else if (projection1[i - 1] == projection2[j - 1]) {
                    lcsMatrix[i][j] = lcsMatrix[i - 1][j - 1] + 1
                } else {
                    lcsMatrix[i][j] = max(lcsMatrix[i - 1][j], lcsMatrix[i][j - 1])
                }
            }
        }
        return lcsMatrix[n][m]
    }

    fun calculateDtwSimilarity(
        horizontalProjection1: List<Int>,
        verticalProjection1: List<Int>,
        horizontalProjection2: List<Int>,
        verticalProjection2: List<Int>
    ): Double {
        val hDtw = dtwDistance(horizontalProjection1, horizontalProjection2)
        val hDtwSimilarity =
            1 - (hDtw / (horizontalProjection1.size.toDouble() + horizontalProjection1.size.toDouble()))
        val vDtw = dtwDistance(verticalProjection1, verticalProjection2)
        val vDtwSimilarity =
            1 - (vDtw / (verticalProjection1.size.toDouble() + verticalProjection2.size.toDouble()))
        return hDtwSimilarity * 0.5 + vDtwSimilarity * 0.5
    }

    fun calculateLcsSimilarity(
        horizontalProjection1: List<Int>,
        verticalProjection1: List<Int>,
        horizontalProjection2: List<Int>,
        verticalProjection2: List<Int>
    ): Double {
        val hLcs = lcsLength(horizontalProjection1, horizontalProjection2)
        val hLcsSimilarity =
            hLcs.toDouble() / minOf(horizontalProjection1.size, horizontalProjection2.size)
        val vLcs = lcsLength(verticalProjection1, verticalProjection2)
        val vLcsSimilarity =
            vLcs.toDouble() / minOf(verticalProjection1.size, verticalProjection2.size)
        return hLcsSimilarity * 0.5 + vLcsSimilarity * 0.5
    }

    fun calculateSimilarity(
        horizontalProjection1: List<Int>,
        verticalProjection1: List<Int>,
        horizontalProjection2: List<Int>,
        verticalProjection2: List<Int>
    ): Double {
        val hDtw = dtwDistance(horizontalProjection1, horizontalProjection2)
        val hDtwSimilarity =
            1 - (hDtw / (horizontalProjection1.size.toDouble() + horizontalProjection1.size.toDouble()))
        val vDtw = dtwDistance(verticalProjection1, verticalProjection2)
        val vDtwSimilarity =
            1 - (vDtw / (verticalProjection1.size.toDouble() + verticalProjection2.size.toDouble()))
        val hLcs = lcsLength(horizontalProjection1, horizontalProjection2)
        val hLcsSimilarity =
            hLcs.toDouble() / minOf(horizontalProjection1.size, horizontalProjection2.size)
        val vLcs = lcsLength(verticalProjection1, verticalProjection2)
        val vLcsSimilarity =
            vLcs.toDouble() / minOf(verticalProjection1.size, verticalProjection2.size)
        return hDtwSimilarity * 0.3 + vDtwSimilarity * 0.2 + hLcsSimilarity * 0.2 + vLcsSimilarity * 0.3
    }

}