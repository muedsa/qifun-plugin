package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCard
import com.muedsa.tvbox.api.data.MediaCardRow
import com.muedsa.tvbox.api.service.IMediaSearchService
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.qifun.service.MainScreenService.Companion.getRelativeUrl
import com.muedsa.tvbox.tool.feignChrome
import org.jsoup.Jsoup
import java.net.CookieStore

class MediaSearchService(
    private val cookieStore: CookieStore
) : IMediaSearchService {
    override suspend fun searchMedias(query: String): MediaCardRow {
        val body = Jsoup.connect("${QiFunConsts.SITE_URL}/vodsearch/-------------.html?wd=$query")
            .feignChrome(cookieStore = cookieStore)
            .get()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            throw RuntimeException("网站需要验证码, 暂未实现验证码功能")
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