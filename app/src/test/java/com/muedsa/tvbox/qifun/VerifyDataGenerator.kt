package com.muedsa.tvbox.qifun

import android.graphics.BitmapFactory
import android.graphics.Color
import com.muedsa.tvbox.qifun.helper.BitmapTool
import com.muedsa.tvbox.qifun.helper.MathHelper
import com.muedsa.tvbox.tool.createOkHttpClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VerifyDataGenerator {

    private val okHttpClient = createOkHttpClient()
    private val json = Json {
        prettyPrint = true
    }

    private val verifyDataFile = File("src/test/verifyData.json").apply {
        println("数据集位置: $absolutePath")
    }
    private val shellFile = File("src/test/shellConsole").apply {
        println("结果位置: $absolutePath")
        writeText("")
    }

    private val imgFile = File("src/test/image.png").apply {
        println("图片位置: $absolutePath")
    }

    @Test
    fun generate() {
        val data: MutableList<VerifyData> = if (verifyDataFile.exists()) {
            json.decodeFromString(verifyDataFile.readText())
        } else {
            mutableListOf()
        }
        while (true) {
            try {
                if (!once(data = data)) {
                    break
                }
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
                break
            }
        }
    }

    private fun once(data: MutableList<VerifyData>): Boolean {
        val imgResp = okHttpClient.newCall(
            Request.Builder()
                .url("${QiFunConsts.SITE_URL}/index.php/verify/index.html?")
                .get()
                .build()
        ).execute()
        val imgByteArray = imgResp.body!!.bytes()
        val bitmap = BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
        val binaryBitmap = BitmapTool.toBinaryBitmap(bitmap = bitmap, flag = 160)
        println("二值图\n${BitmapTool.binaryBitmapToPrintString(binaryBitmap = binaryBitmap)}")
        val projectionBitmaps = BitmapTool.splitProjectionBitmap(
            bitmap = binaryBitmap,
            horizontalWeightFun = { w, h ->
                if (binaryBitmap.getPixel(w, h) == Color.BLACK) 1 else 0
            },
            verticalWeightFun = { w, h ->
                if (binaryBitmap.getPixel(w, h) == Color.BLACK) 1 else 0
            }
        )
        if (projectionBitmaps.size == 4) {
            imgFile.writeBytes(imgByteArray)
            projectionBitmaps.forEachIndexed { index, item ->
                val imgStr = BitmapTool.binaryBitmapToPrintString(binaryBitmap = item.bitmap)
                checkProjection(data, item.horizontalProjection, item.verticalProjection)
                // val input = readlnOrNull()
                println("拆分$index\n$imgStr")
                when (val n = readResultFromShellFile(timeout = 60 * 3)) {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                        println("保存为${n}")
                        data.add(
                            VerifyData(
                                result = n.digitToInt(),
                                horizontalProjection = item.horizontalProjection,
                                verticalProjection = item.verticalProjection,
                                imgStr = imgStr
                            )
                        )
                        verifyDataFile.writeText(json.encodeToString(data))
                    }

                    's', 'S' -> return false
                    'r', 'R' -> {
                        data.removeLast()
                        verifyDataFile.writeText(json.encodeToString(data))
                    }
                }
            }
        }
        return true
    }

    private fun checkProjection(
        data: MutableList<VerifyData>,
        horizontalProjection: List<Int>,
        verticalProjection: List<Int>,
    ) {
        var similarity = -99.9
        var dSimilarity = -99.9
        var lSimilarity = -99.9
        var similarityItem: VerifyData? = null
        data.forEach {
            val d = MathHelper.calculateDtwSimilarity(
                horizontalProjection1 = it.horizontalProjection,
                verticalProjection1 = it.verticalProjection,
                horizontalProjection2 = horizontalProjection,
                verticalProjection2 = verticalProjection
            )
            val l = MathHelper.calculateLcsSimilarity(
                horizontalProjection1 = it.horizontalProjection,
                verticalProjection1 = it.verticalProjection,
                horizontalProjection2 = horizontalProjection,
                verticalProjection2 = verticalProjection
            )
            val s = MathHelper.calculateSimilarity(
                horizontalProjection1 = it.horizontalProjection,
                verticalProjection1 = it.verticalProjection,
                horizontalProjection2 = horizontalProjection,
                verticalProjection2 = verticalProjection
            )
            if (s > similarity) {
                similarity = s
                dSimilarity = d
                lSimilarity = l
                similarityItem = it
            }
        }
        if (similarityItem != null) {
            println("对比结果集(size=${data.size})与${similarityItem!!.result}最相似\n${similarityItem!!.imgStr}\n相似度${similarity} $dSimilarity $lSimilarity")
        }
    }

    @Test
    fun checkVerifyDataJson() {
        val reverse = false
        val onlyChars: List<Int> = listOf(1, 7)
        val skip = 420
        val data: MutableList<VerifyData> = if (verifyDataFile.exists()) {
            json.decodeFromString(verifyDataFile.readText())
        } else {
            mutableListOf()
        }
        if (reverse) {
            data.reverse()
        }
        val iterator = data.iterator()
        var i = -1
        while (iterator.hasNext()) {
            i++
            val item = iterator.next()
            if (i > skip) {
                if (onlyChars.isEmpty() || onlyChars.contains(item.result)) {
                    println("当前$i: \n${item.imgStr} \n${item.result}")
                    when (val n = readResultFromShellFile(timeout = 200)) {
                        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> item.result =
                            n.digitToInt()

                        'n', 'N' -> iterator.remove()
                        's', 'S' -> break
                    }
                }
            }
        }
        if (reverse) {
            data.reverse()
        }
        verifyDataFile.writeText(json.encodeToString(data))
    }

    private fun readResultFromShellFile(timeout: Int): Char {
        var i = 0
        while (i < timeout) {
            Thread.sleep(1000)
            val input = shellFile.readText()
            if (input.isNotBlank()) {
                shellFile.writeText("")
                return input[0]
            }
            i++
        }
        return 'P'
    }


    @Test
    fun duplicateData() {
        val verifyDataList: MutableList<VerifyData> = if (verifyDataFile.exists()) {
            json.decodeFromString(verifyDataFile.readText())
        } else {
            mutableListOf()
        }
        val duplicateData = mutableListOf<VerifyData>()
        val offsetList = mutableListOf<String>()
        val indexList = mutableListOf<Int>()
        for (i in verifyDataList.indices) {
            for (j in i + 1 until verifyDataList.size) {
                val data1 = verifyDataList[i]
                val data2 = verifyDataList[j]
                if (data1.horizontalProjection == data2.horizontalProjection &&
                    data1.verticalProjection == data2.verticalProjection
                ) {
                    if (!duplicateData.contains(data1)) {
                        duplicateData.add(data1)
                        offsetList.add("$i - $j")
                        indexList.add(i)
                    }
                    if (!duplicateData.contains(data2)) {
                        duplicateData.add(data2)
                        println(data2)
                    }
                }
            }
        }
        duplicateData.forEachIndexed { index, it ->
            println("offset=${offsetList[index]}, result=${it.result}, horizontalProjection=${it.horizontalProjection}, verticalProjection=${it.verticalProjection}")
            println(it.imgStr)
        }

        val iterator = verifyDataList.iterator()
        var i = -1
        while (iterator.hasNext()) {
            iterator.next()
            i++
            if (indexList.contains(i)) {
                iterator.remove()
            }
        }
        verifyDataFile.writeText(json.encodeToString(verifyDataList))
    }

    @Test
    fun generateKotlinCode() {
        val verifyDataList: MutableList<VerifyData> = if (verifyDataFile.exists()) {
            json.decodeFromString(verifyDataFile.readText())
        } else {
            mutableListOf()
        }
        val maxSize = 200
        val splitNumber = if (verifyDataList.size % maxSize == 0) {
            verifyDataList.size / maxSize
        } else {
            verifyDataList.size / maxSize + 1
        }
        var ktCode = "package com.muedsa.tvbox.qifun.data\n\n" +
                "val VERIFY_DATA_SET: List<Triple<List<Int>, List<Int>, Int>> = buildList{\n"
        for (i in 0 until splitNumber) {
            println("generate build/generated/tempCode/VerifyDataSet$i.kt")
            val dataList = verifyDataList.subList(i * maxSize, min(i * maxSize + maxSize, verifyDataList.size))
            var splitKtCode = "package com.muedsa.tvbox.qifun.data\n\n" +
                    "val VERIFY_DATA_SET_$i: List<Triple<List<Int>, List<Int>, Int>> = listOf(\n"
            splitKtCode += dataList.joinToString(",\n") {
                StringBuilder().apply {
                    append("    Triple(\n")
                    append("        listOf(${it.horizontalProjection.joinToString(", ")}),\n")
                    append("        listOf(${it.verticalProjection.joinToString(", ")}),\n")
                    append("        ${it.result}\n")
                    append("    )")
                }.toString()
            }
            splitKtCode += "\n)"
            // println(splitKtCode)
            File("build/generated/tempCode/VerifyDataSet$i.kt")
                .apply {
                    println("create $absolutePath")
                    parentFile?.mkdirs()
                }
                .writeText(splitKtCode)
            ktCode += "    addAll(VERIFY_DATA_SET_$i)\n"
        }
        ktCode += "}"
        File("build/generated/tempCode/VerifyDataSet.kt")
            .apply {
                println("create $absolutePath")
                parentFile?.mkdirs()
            }
            .writeText(ktCode)
    }
}