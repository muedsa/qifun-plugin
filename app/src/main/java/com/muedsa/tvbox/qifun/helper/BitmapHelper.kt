package com.muedsa.tvbox.qifun.helper

import android.graphics.Bitmap
import android.graphics.Color

object BitmapTool {

    fun toBinaryBitmap(bitmap: Bitmap, flag: Int = 127): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (i in 0 until width) {
            for (j in 0 until height) {
                val pixelColor = bitmap.getPixel(i, j)
                // 灰度值
                val grayValue = Color.red(pixelColor) * 0.299 +
                        Color.green(pixelColor) * 0.587 +
                        Color.blue(pixelColor) * 0.114
                // 二值化
                if (grayValue >= flag) {
                    newBitmap.setPixel(i, j, Color.WHITE)
                } else {
                    newBitmap.setPixel(i, j, Color.BLACK)
                }
            }
        }
        return newBitmap
    }

    fun splitProjectionBitmap(
        bitmap: Bitmap,
        horizontalWeightFun:(Int, Int) -> Int,
        verticalWeightFun:(Int, Int) -> Int
    ): List<ProjectionBitmap> {
        val width = bitmap.width
        val height = bitmap.height
        val projectionBitmaps = mutableListOf<ProjectionBitmap>()
        // 先通过水平投影 进行分割
        val horizontalProjections = IntArray(width)
        val horizontalRangeList: MutableList<IntRange> = mutableListOf()
        var horizontalOffset = -1
        for (w in 0 until width) {
            for (h in 0 until height) {
                horizontalProjections[w] += horizontalWeightFun(w, h)
            }
            if (horizontalProjections[w] > 0) {
                if (horizontalOffset < 0) {
                    // 之前没有值 现在有了
                    // 需要标记这个位置
                    horizontalOffset = w
                }
            } else {
                if(horizontalOffset >= 0) {
                    horizontalRangeList.add(horizontalOffset until w)
                    horizontalOffset = -1
                }
            }
        }
        horizontalRangeList.forEach { range ->
            val verticalProjection = IntArray(height)
            var verticalStart = -1
            var verticalEnd = -1
            for (h in 0 until height) {
                for (w in range) {
                    verticalProjection[h] += verticalWeightFun(w, h)
                }
                if (verticalProjection[h] > 0) {
                    if (verticalStart < 0) {
                        // 标记第一次有值的位置
                        verticalStart = h
                    }
                    // 标记最后有值的位置
                    verticalEnd = h
                }
            }
            if (verticalStart >= 0 && verticalEnd >= 0) {
                projectionBitmaps.add(
                    ProjectionBitmap(
                        bitmap = Bitmap.createBitmap(
                            bitmap,
                            range.first,
                            verticalStart,
                            range.last - range.first + 1,
                            verticalEnd - verticalStart + 1
                        ),
                        horizontalProjection = horizontalProjections.copyOfRange(
                            fromIndex = range.first,
                            toIndex = range.last + 1
                        ).asList(),
                        verticalProjection = verticalProjection.copyOfRange(
                            fromIndex = verticalStart,
                            toIndex = verticalEnd + 1
                        ).asList()
                    )
                )
            }
        }
        return projectionBitmaps
    }

    fun binaryBitmapToPrintString(binaryBitmap: Bitmap): String {
        val width = binaryBitmap.width
        val height = binaryBitmap.height
        val stringBuilder = StringBuilder()
        for (h in 0 until height) {
            for (w in 0 until width) {
                if (binaryBitmap.getPixel(w, h) == Color.BLACK) {
                    stringBuilder.append("█")
                } else {
                    stringBuilder.append("░")
                }
            }
            if (h < height - 1) {
                stringBuilder.append("\n")
            }
        }
        return stringBuilder.toString()
    }

}

class ProjectionBitmap(
    val bitmap: Bitmap,
    val horizontalProjection: List<Int>,
    val verticalProjection: List<Int>
)