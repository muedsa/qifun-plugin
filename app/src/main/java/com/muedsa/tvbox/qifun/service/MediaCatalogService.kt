package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCard
import com.muedsa.tvbox.api.data.MediaCatalogConfig
import com.muedsa.tvbox.api.data.MediaCatalogOption
import com.muedsa.tvbox.api.data.MediaCatalogOptionItem
import com.muedsa.tvbox.api.data.PagingResult
import com.muedsa.tvbox.api.service.IMediaCatalogService
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.qifun.service.MainScreenService.Companion.getRelativeUrl
import com.muedsa.tvbox.tool.checkSuccess
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.get
import com.muedsa.tvbox.tool.parseHtml
import com.muedsa.tvbox.tool.toRequestBuild
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

class MediaCatalogService(
    private val verifyService: VerifyService,
    private val okHttpClient: OkHttpClient
) : IMediaCatalogService {

    override suspend fun getConfig(): MediaCatalogConfig {
        val options = verifyAndGetOptions(verified = false)
        return MediaCatalogConfig(
            initKey = "1",
            pageSize = 24,
            cardWidth = QiFunConsts.CARD_WIDTH,
            cardHeight = QiFunConsts.CARD_HEIGHT,
            catalogOptions = buildList {
                add(
                    MediaCatalogOption(
                        name = "排序",
                        value = "order",
                        items = listOf(
                            MediaCatalogOptionItem(
                                name = "最近更新",
                                value = "time",
                                defaultChecked = true
                            ),
                            MediaCatalogOptionItem(
                                name = "最多观看",
                                value = "hits",
                            ),
                            MediaCatalogOptionItem(
                                name = "最佳口碑",
                                value = "score",
                            )
                        ),
                        required = true
                    )
                )
                add(
                    MediaCatalogOption(
                        name = "类型",
                        value = "category",
                        items = listOf(
                            MediaCatalogOptionItem(
                                name = "TV动画",
                                value = "1",
                                defaultChecked = true
                            ),
                            MediaCatalogOptionItem(
                                name = "剧场版",
                                value = "2",
                            ),
                        ),
                        required = true
                    )
                )
                addAll(options)
            }
        )
    }

    private suspend fun verifyAndGetOptions(verified: Boolean): List<MediaCatalogOption> {
        val url = "${QiFunConsts.SITE_URL}/vodshow/1--time---------.html"
        val body = url.toRequestBuild()
            .feignChrome()
            .get(okHttpClient = okHttpClient)
            .checkSuccess()
            .parseHtml()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            if (body.selectFirst(".mac_msg_jump .mac_verify") != null) {
                if (verified || !verifyService.tryVerify("show")) {
                    throw RuntimeException("网站需要验证码，但尝试识别验证码失败，重试操作再次尝试验证")
                } else {
                    delay(3000)
                    return verifyAndGetOptions(verified = true)
                }
            } else {
                throw RuntimeException(
                    body.selectFirst(".mac_msg_jump >.text")?.text() ?: "请求被阻止，请稍后重试"
                )
            }
        }

        return body.select(".site-content .content-header .video-filter .filter-box").mapNotNull { filterEl ->
            val title = filterEl.selectFirst(".filter-title")?.text()?.trim()?.removePrefix("：")
            val swiperSlideEls = filterEl.select(".swiper .swiper-slide")
            if (title != null && swiperSlideEls.isNotEmpty()) {
                MediaCatalogOption(
                    name = title,
                    value = title,
                    items = swiperSlideEls.map { swiperSlideEl ->
                        val itemName = swiperSlideEl.text().trim()
                        MediaCatalogOptionItem(
                            name = itemName,
                            value = if(itemName == "全部") "" else itemName,
                            defaultChecked = itemName == "全部"
                        )
                    },
                    required = true
                )
            } else null
        }
    }


    override suspend fun catalog(
        options: List<MediaCatalogOption>,
        loadKey: String,
        loadSize: Int
    ): PagingResult<MediaCard> {
        // 1-日本-time-游戏改-日语-A---1---2024年10月
        val category = options.find { option -> option.value == "category" }?.items[0]?.value ?: ""
        val region = options.find { option -> option.value == "地区" }?.items[0]?.value ?: ""
        val order = options.find { option -> option.value == "order" }?.items[0]?.value ?: ""
        val label = options.find { option -> option.value == "剧情" }?.items[0]?.value ?: ""
        val language = options.find { option -> option.value == "语言" }?.items[0]?.value ?: ""
        val letter = options.find { option -> option.value == "字母" }?.items[0]?.value ?: ""
        val year = options.find { option -> option.value == "年份" }?.items[0]?.value ?: ""
        val url = "${QiFunConsts.SITE_URL}/vodshow/$category-$region-$order-$label-$language-$letter---$loadKey---$year.html"
        val body = url.toRequestBuild()
            .feignChrome()
            .get(okHttpClient = okHttpClient)
            .checkSuccess()
            .parseHtml()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            throw RuntimeException(
                body.selectFirst(".mac_msg_jump >.text")?.text() ?: "请求被阻止，请稍后重试"
            )
        }
        val cards = body.select(".site-content .container section .video-img-box").mapNotNull { boxEl ->
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
        val pageLinkEls = body.select(".site-content .container section .pagination .page-item .page-link[href]")

        return PagingResult(
            list = cards,
            prevKey = pageLinkEls.find { it.text().contains("上一页") }?.let{
                val p = urlToPage(it.attr("href"))
                if (p == loadKey) null else p
            },
            nextKey = pageLinkEls.find { it.text().contains("下一页") }?.let{
                val p = urlToPage(it.attr("href"))
                if (p == loadKey) null else p
            }
        )
    }

    companion object {
        val URL_PAGE_REGEX = "/vodshow/\\w*-\\w*-\\w*-\\w*-\\w*-\\w*---(\\w*)---\\w*.html".toRegex()
        fun urlToPage(url: String) = URL_PAGE_REGEX.find(url)?.groups[1]?.value ?: "1"
    }
}