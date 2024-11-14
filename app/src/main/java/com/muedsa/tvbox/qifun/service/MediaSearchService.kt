package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCard
import com.muedsa.tvbox.api.data.MediaCardRow
import com.muedsa.tvbox.api.service.IMediaSearchService
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.qifun.service.MainScreenService.Companion.getRelativeUrl
import com.muedsa.tvbox.tool.checkSuccess
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.get
import com.muedsa.tvbox.tool.parseHtml
import com.muedsa.tvbox.tool.toRequestBuild
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

class MediaSearchService(
    private val verifyService: VerifyService,
    private val okHttpClient: OkHttpClient
) : IMediaSearchService {
    override suspend fun searchMedias(query: String): MediaCardRow =
        verifyAndSearch(query = query, verified = false)

    private suspend fun verifyAndSearch(query: String, verified: Boolean): MediaCardRow {
        val url = "${QiFunConsts.SITE_URL}/vodsearch/-------------.html?wd=$query"
        val body = url.toRequestBuild()
            .feignChrome()
            .get(okHttpClient = okHttpClient)
            .checkSuccess()
            .parseHtml()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            if (body.selectFirst(".mac_msg_jump .mac_verify") != null) {
                if (verified || !verifyService.tryVerify("search")) {
                    throw RuntimeException("网站需要验证码，但尝试识别验证码失败，重试操作再次尝试验证")
                } else {
                    delay(3000)
                    return verifyAndSearch(query = query, verified = true)
                }
            } else {
                throw RuntimeException(
                    body.selectFirst(".mac_msg_jump >.text")?.text() ?: "请求被阻止，请稍后重试"
                )
            }
        }
        val cards = body.select(".site-content .container .video-img-box").mapNotNull { boxEl ->
            val imgEl = boxEl.selectFirst(".img-box img")
            val detailUrl = boxEl.selectFirst(".img-box a")?.attr("href")
            val cardTitle = boxEl.selectFirst(".detail .title")?.text()?.trim()
            val cardSubTitle = boxEl.selectFirst(".detail .sub-title")?.text()?.trim()
            if (imgEl != null && detailUrl != null && cardTitle != null
                && MediaDetailService.isDetailUrl(detailUrl)
            ) {
                val relativeDetailUrl = getRelativeUrl(detailUrl)
                MediaCard(
                    id = relativeDetailUrl,
                    title = cardTitle,
                    detailUrl = relativeDetailUrl,
                    subTitle = cardSubTitle,
                    coverImageUrl = imgEl.attr("data-original")
                )
            } else null
        }
        return MediaCardRow(
            title = "search list",
            cardWidth = QiFunConsts.CARD_WIDTH,
            cardHeight = QiFunConsts.CARD_HEIGHT,
            list = cards
        )
    }
}