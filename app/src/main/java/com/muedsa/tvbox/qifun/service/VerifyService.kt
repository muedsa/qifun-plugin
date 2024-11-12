package com.muedsa.tvbox.qifun.service

import android.graphics.BitmapFactory
import android.graphics.Color
import com.muedsa.tvbox.qifun.helper.BitmapTool
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.qifun.data.VERIFY_DATA_SET
import com.muedsa.tvbox.qifun.helper.MathHelper
import com.muedsa.tvbox.qifun.model.VerifyResult
import com.muedsa.tvbox.tool.LenientJson
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.get
import com.muedsa.tvbox.tool.post
import com.muedsa.tvbox.tool.toRequestBuild
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import timber.log.Timber


class VerifyService(
    private val okHttpClient: OkHttpClient
) {

    private val maxRetryTimes = 10

    suspend fun tryVerify(type: String = "search"): Boolean {
        for (i in 0..maxRetryTimes) {
            val imgResp = "${QiFunConsts.SITE_URL}/index.php/verify/index.html?".toRequestBuild()
                .feignChrome()
                .get(okHttpClient = okHttpClient)
            val imgByteArray = imgResp.body!!.bytes()
            val bitmap = BitmapFactory.decodeByteArray(imgByteArray, 0, imgByteArray.size)
            val binaryBitmap = BitmapTool.toBinaryBitmap(bitmap = bitmap, flag = 127)
            Timber.i("二值图\n${BitmapTool.binaryBitmapToPrintString(binaryBitmap = binaryBitmap)}")
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
                val verifyCode = projectionBitmaps.mapIndexed { index, item ->
                    Timber.i("拆分$index\n${BitmapTool.binaryBitmapToPrintString(binaryBitmap = item.bitmap)}")
                    getSimilarityResult(
                        horizontalProjection = item.horizontalProjection,
                        verticalProjection = item.verticalProjection
                    )
                }.joinToString("") { it.toString() }
                Timber.i("验证码可能是:$verifyCode")
                val verifyResp = "${QiFunConsts.SITE_URL}/index.php/ajax/verify_check?type=${type}&verify=${verifyCode}"
                    .toRequestBuild()
                    .feignChrome()
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .post(body = FormBody.Builder().build(), okHttpClient = okHttpClient)
                if (verifyResp.isSuccessful) {
                    val verifyResultJson = verifyResp.body!!.string()
                    Timber.i("验证结果:$verifyResultJson")
                    val verifyResult = LenientJson.decodeFromString<VerifyResult>(verifyResultJson)
                    if (verifyResult.code == 1) {
                        delay(200)
                        return true
                    }
                }
            }
            delay(200)
        }
        return false
    }

    private fun getSimilarityResult(
        horizontalProjection: List<Int>,
        verticalProjection: List<Int>
    ): Int {
        var similarity = -99.9
        var result = -1
        VERIFY_DATA_SET.forEach {
            val s = MathHelper.calculateSimilarity(
                horizontalProjection1 = it.first,
                verticalProjection1 = it.second,
                horizontalProjection2 = horizontalProjection,
                verticalProjection2 = verticalProjection
            )
            if (s > similarity) {
                similarity = s
                result = it.third
            }
        }
        return result
    }
}