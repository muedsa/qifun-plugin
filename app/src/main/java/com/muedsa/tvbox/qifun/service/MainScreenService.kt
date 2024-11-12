package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCard
import com.muedsa.tvbox.api.data.MediaCardRow
import com.muedsa.tvbox.api.service.IMainScreenService
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.get
import com.muedsa.tvbox.tool.parseHtml
import com.muedsa.tvbox.tool.toRequestBuild
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class MainScreenService(
    private val okHttpClient: OkHttpClient
) : IMainScreenService {

    override suspend fun getRowsData(): List<MediaCardRow> {
        val body = "${QiFunConsts.SITE_URL}/".toRequestBuild()
            .feignChrome()
            .get(okHttpClient = okHttpClient)
            .parseHtml()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            throw RuntimeException("网站需要验证码, 暂未实现验证码功能")
        }
        val rows = mutableListOf<MediaCardRow>()
        // appendTop(body = body, rows = rows)
        appendSections(body = body, rows = rows)
        appendLabelRow(labelPath = "/all.html", rowTitle = "最近更新", rows = rows)
        appendLabelRow(labelPath = "/hot.html", rowTitle = "最多观看", rows = rows)
        appendLabelRow(labelPath = "/up.html", rowTitle = "最近口碑", rows = rows)
        return rows
    }

    private fun appendTop(body: Element, rows: MutableList<MediaCardRow>) {
        val slideEls = body.select("#site-content #topSwiper .swiper .swiper-wrapper .swiper-slide")
        val cards = slideEls.mapNotNull { slideEl ->
            val imgEl = slideEl.selectFirst(".img-box img")
            val detailUrl = slideEl.selectFirst(".img-box a")?.attr("href")
            if (imgEl != null && detailUrl != null && MediaDetailService.isDetailUrl(detailUrl)) {
                val relativeDetailUrl = getRelativeUrl(detailUrl)
                MediaCard(
                    id = relativeDetailUrl,
                    title = imgEl.attr("alt"),
                    detailUrl = relativeDetailUrl,
                    coverImageUrl = imgEl.attr("data-original")
                )
            } else null
        }
        if (cards.isNotEmpty()) {
            rows.add(MediaCardRow(
                title = "热门",
                cardWidth = QiFunConsts.CARD_WIDTH,
                cardHeight = QiFunConsts.CARD_HEIGHT,
                list = cards
            ))
        }
    }

    private fun appendSections(body: Element, rows: MutableList<MediaCardRow>) {
        val sectionEls = body.select("#site-content .container section")
        sectionEls.forEach {
            val rowTitle = it.selectFirst(".title-box h2")?.text()?.trim()
            if (rowTitle != null) {
                appendSection(rowTitle = rowTitle, sectionEl = it, rows = rows)
            }
        }
    }

    private fun appendSection(rowTitle: String, sectionEl: Element, rows: MutableList<MediaCardRow>) {
        val cards = sectionEl.select(".video-img-box").mapNotNull { boxEl ->
            val imgEl = boxEl.selectFirst(".img-box img")
            val detailUrl = boxEl.selectFirst(".img-box a")?.attr("href")
            val cardTitle = boxEl.selectFirst(".detail .title")?.text()?.trim()
            val cardSubTitle = boxEl.selectFirst(".detail .sub-title")?.text()?.trim()
            if (imgEl != null && detailUrl != null && cardTitle != null && MediaDetailService.isDetailUrl(detailUrl)) {
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
        if (cards.isNotEmpty()) {
            rows.add(MediaCardRow(
                title = rowTitle,
                cardWidth = QiFunConsts.CARD_WIDTH,
                cardHeight = QiFunConsts.CARD_HEIGHT,
                list = cards
            ))
        }
    }

    private fun appendLabelRow(labelPath: String, rowTitle: String, rows: MutableList<MediaCardRow>) {
        val body = "${QiFunConsts.SITE_URL}/label${labelPath}".toRequestBuild()
            .feignChrome()
            .get(okHttpClient = okHttpClient)
            .parseHtml()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            throw RuntimeException(
                body.selectFirst(".mac_msg_jump >.text")?.text() ?: "请求被阻止，请稍后重试"
            )
        }
        body.selectFirst(".site-content .container section")?.let {
            appendSection(rowTitle= rowTitle, sectionEl = it, rows = rows)
        }
    }

    companion object {
        fun getRelativeUrl(detailUrl: String): String =
            detailUrl.removePrefix(QiFunConsts.SITE_URL)
    }
}