package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCard
import com.muedsa.tvbox.api.data.MediaCardRow
import com.muedsa.tvbox.api.data.MediaDetail
import com.muedsa.tvbox.api.data.MediaEpisode
import com.muedsa.tvbox.api.data.MediaHttpSource
import com.muedsa.tvbox.api.data.MediaPlaySource
import com.muedsa.tvbox.api.data.SavedMediaCard
import com.muedsa.tvbox.api.service.IMediaDetailService
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.qifun.model.PlayerAAAA
import com.muedsa.tvbox.tool.LenientJson
import com.muedsa.tvbox.tool.decodeBase64ToStr
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.md5
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.CookieStore

class MediaDetailService(
    private val cookieStore: CookieStore
) : IMediaDetailService {

    override suspend fun getDetailData(mediaId: String, detailUrl: String): MediaDetail {
        if (!isDetailUrl(detailUrl)) {
            throw RuntimeException("不支持的类型")
        }
        val body = Jsoup.connect("${QiFunConsts.SITE_URL}$detailUrl")
            .feignChrome(cookieStore = cookieStore)
            .get()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            throw RuntimeException("网站需要验证码, 暂未实现验证码功能")
        }
        val thumbAEl = body.select(".site-content .container .detail .detail-thumb a").find {
            !it.hasClass("d-sm-none")
        } ?: throw RuntimeException("解析视频详情失败 thumbAEl")
        val parseDetailUrl = thumbAEl.attr("href")
        val relativeDetailUrl = if (isDetailUrl(parseDetailUrl)) {
            MainScreenService.getRelativeUrl(parseDetailUrl)
        } else detailUrl
        val imgUrl = thumbAEl.selectFirst("img")?.attr("data-original")
            ?: throw RuntimeException("解析视频详情失败 thumbImgEl")
        val infoEl = body.selectFirst(".site-content .container .detail .detail-info")
            ?: throw RuntimeException("解析视频详情失败 infoEl")
        val title = infoEl.selectFirst("h1")?.text()?.trim()
            ?: throw RuntimeException("解析视频详情失败 title")
        val descriptionArr = mutableListOf<String>()
        val excerpt = infoEl.selectFirst(".info-excerpt")
            ?.children()
            ?.mapNotNull {
                val e = it.text().trim()
                e.ifBlank { null }
            }
            ?.joinToString(" | ")
        infoEl.selectFirst(".info-tags")
            ?.children()
            ?.mapNotNull {
                val e = it.text().trim()
                e.ifBlank { null }
            }
            ?.joinToString(" | ")
            ?.let { descriptionArr.add(it) }
        infoEl.selectFirst(".info-btn .info-content")?.text()?.trim()?.let {
            descriptionArr.add(it)
        }
        val rows = mutableListOf<MediaCardRow>()
        appendRightSidebarRow(body = body, rows = rows)
        return MediaDetail(
            id = relativeDetailUrl,
            title = title,
            subTitle = excerpt,
            description = descriptionArr.joinToString("\n"),
            detailUrl = relativeDetailUrl,
            backgroundImageUrl = imgUrl,
            playSourceList = getPlaySourceList(body = body, detailUrl = detailUrl),
            favoritedMediaCard = SavedMediaCard(
                id = relativeDetailUrl,
                title = title,
                detailUrl = relativeDetailUrl,
                coverImageUrl = imgUrl,
                cardWidth = QiFunConsts.CARD_WIDTH,
                cardHeight = QiFunConsts.CARD_HEIGHT,
            ),
            rows = rows
        )
    }

    private fun getPlaySourceList(body: Element, detailUrl: String): List<MediaPlaySource> {
        val sectionEl = body.selectFirst(".site-content .container .episode") ?: return emptyList()
        val psEls = sectionEl.select(".play_source .swiper-wrapper .swiper-slide")
        val epRowEls = sectionEl.select(".play_source #tagContent >.row")
        return psEls.mapIndexedNotNull { index, psEl ->
            if (index < epRowEls.size) {
                val psId = psEl.ownText().trim()
                val badgeEl = psEl.selectFirst(">.badge")
                val psName = if (badgeEl != null) "$psId(${badgeEl.text().trim()})" else psId
                MediaPlaySource(
                    id = psId,
                    name = psName,
                    episodeList = epRowEls[index].select(".ep-item a").map {
                        val epName = it.text().trim()
                        MediaEpisode(
                            id = epName,
                            name = epName,
                            flag5 = MainScreenService.getRelativeUrl(it.attr("href")),
                            flag6 = "${QiFunConsts.SITE_URL}$detailUrl"
                        )
                    }
                )
            } else null
        }
    }

    private fun appendRightSidebarRow(body: Element, rows: MutableList<MediaCardRow>) {
        val cards = body.select(".site-content .container .row .right-sidebar .video-img-box")
            .mapNotNull { boxEl ->
                val imgEl = boxEl.selectFirst(".img-box img")
                val detailUrl = boxEl.selectFirst(".img-box a")?.attr("href")
                val cardTitle = boxEl.selectFirst(".detail .title")?.text()?.trim()
                val cardSubTitle = boxEl.selectFirst(".detail .sub-title")?.text()?.trim()
                if (imgEl != null && detailUrl != null && cardTitle != null && isDetailUrl(
                        detailUrl
                    )
                ) {
                    val relativeDetailUrl = MainScreenService.getRelativeUrl(detailUrl)
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
            rows.add(
                MediaCardRow(
                    title = "猜你喜欢",
                    cardWidth = QiFunConsts.CARD_WIDTH,
                    cardHeight = QiFunConsts.CARD_HEIGHT,
                    list = cards
                )
            )
        }
    }

    override suspend fun getEpisodePlayInfo(
        playSource: MediaPlaySource,
        episode: MediaEpisode
    ): MediaHttpSource {
        val episodeUrl = episode.flag5 ?: throw RuntimeException("不支持的剧集类型")
        if (!episodeUrl.startsWith("/vodplay")) throw RuntimeException("不支持的剧集类型 $episodeUrl")
        val referrer = episode.flag6 ?: QiFunConsts.SITE_URL
        val playUrl = "${QiFunConsts.SITE_URL}$episodeUrl"
        val body = Jsoup.connect(playUrl)
            .feignChrome(referrer = referrer, cookieStore = cookieStore)
            .get()
            .body()
        if (body.selectFirst(".mac_msg_jump") != null) {
            throw RuntimeException("网站需要验证码, 暂未实现验证码功能")
        }
        val scriptEl =
            body.selectFirst(".site-content .container .row .col .wpmytube-player script")
                ?: throw RuntimeException("解析地址失败 scriptEl")
        val playerAAAAJson = PLAYER_INFO_REGEX.find(scriptEl.outerHtml())
            ?.groups?.get(1)?.value ?: throw RuntimeException("解析地址失败 player_aaaa")
        var playerAAAA = LenientJson.decodeFromString<PlayerAAAA>(playerAAAAJson)
        if (playerAAAA.encrypt == 2) {
            playerAAAA = playerAAAA.copy(
                url = playerAAAA.url.decodeBase64ToStr(),
                urlNext = playerAAAA.urlNext.decodeBase64ToStr()
            )
        }
        return if (playerAAAA.url.endsWith(".m3u8", false)
            || playerAAAA.url.endsWith(".mp4", false)
        ) {
            MediaHttpSource(url = playerAAAA.url, httpHeaders = mapOf("Referrer" to playUrl))
        } else if (PARSE_FN_MAP.containsKey(playerAAAA.from)) {
            step2(playerAAAA = playerAAAA, referrer = playUrl)
        } else {
            MediaHttpSource(url = playerAAAA.url, httpHeaders = mapOf("Referrer" to playUrl))
        }
    }

    private suspend fun step2(playerAAAA: PlayerAAAA, referrer: String): MediaHttpSource {
        delay(200)
        val parseFunction = PARSE_FN_MAP[playerAAAA.from]
            ?: throw RuntimeException("解析地址失败 parseFunction, $playerAAAA")
        return parseFunction.invoke(playerAAAA, referrer, cookieStore)
    }

    companion object {
        fun isDetailUrl(detailUrl: String): Boolean =
            detailUrl.startsWith("${QiFunConsts.SITE_URL}/voddetail")
                    || detailUrl.startsWith("/voddetail")

        private val PLAYER_INFO_REGEX =
            "<script type=\"text/javascript\">var player_aaaa=(\\{.*?\\})</script>".toRegex()
        private val QIFUNQP_VID_REGEX = "var vid = '(.*?)';".toRegex()
        private val DM295_DECODE_URL_REGEX = "url: strdecode\\('(.*?)'\\),".toRegex()
        private val ATQP_URL_REGEX = "var url = '(.*?\\.m3u8)';".toRegex()
        @OptIn(ExperimentalStdlibApi::class)
        val DM295_DECODE_KEY = "405468858".md5().toHexString().toCharArray()

        fun dm295DecodeUrl(str: String): String {
            val charArr = str.decodeBase64ToStr().toCharArray()
            val strBuilder = StringBuilder()
            charArr.forEachIndexed { index, c ->
                strBuilder.append((c.code xor DM295_DECODE_KEY[index % DM295_DECODE_KEY.size].code).toChar())
            }
            return strBuilder.toString().decodeBase64ToStr()
        }

        private val PARSE_FN_MAP =
            mapOf<String, (PlayerAAAA, String, CookieStore) -> MediaHttpSource>(
                "qifunqp" to { playerAAAA, referrer, cookieStore ->
                    val body =
                        Jsoup.connect("https://www.qifun.cc/art/plyr.php?url=${playerAAAA.url}")
                            .feignChrome(referrer = referrer, cookieStore = cookieStore)
                            .get()
                            .body()
                    val vid = QIFUNQP_VID_REGEX.find(body.html())?.groups?.get(1)?.value
                        ?: throw RuntimeException("解析播放源地址失败 qifunqp->vid")
                    MediaHttpSource(url = vid, httpHeaders = mapOf("Referrer" to referrer))
                },
                "dm295" to { playerAAAA, referrer, cookieStore ->
                    val body =
                        Jsoup.connect("https://www.qifun.cc/art/qf.php?url=${playerAAAA.url}")
                            .feignChrome(referrer = referrer, cookieStore = cookieStore)
                            .get()
                            .body()
                    val decodeUrl = DM295_DECODE_URL_REGEX.find(body.html())?.groups?.get(1)?.value
                        ?: throw RuntimeException("解析播放源地址失败 dm295->decodeUrl")
                    MediaHttpSource(
                        url = dm295DecodeUrl(decodeUrl),
                        httpHeaders = mapOf("Referrer" to referrer)
                    )
                },
                "tk" to { _, _, _ ->
                    // https://www.qifun.cc/art/tkzy.php?url=
                    throw RuntimeException("不可用的播放源")
                },
                "ATQP" to { playerAAAA, referrer, cookieStore ->
                    val body =
                        Jsoup.connect("https://www.qifun.cc/art/aut.php?url=${playerAAAA.url}")
                            .feignChrome(referrer = referrer, cookieStore = cookieStore)
                            .get()
                            .body()
                    var decodeUrl = ATQP_URL_REGEX.find(body.html())?.groups?.get(1)?.value
                    if (decodeUrl != null) {
                        MediaHttpSource(
                            url = decodeUrl,
                            httpHeaders = mapOf("Referrer" to referrer)
                        )
                    } else {
                        decodeUrl = DM295_DECODE_URL_REGEX.find(body.html())?.groups?.get(1)?.value
                            ?: throw RuntimeException("解析播放源地址失败 ATQP->decodeUrl")
                        MediaHttpSource(
                            url = dm295DecodeUrl(decodeUrl),
                            httpHeaders = mapOf("Referrer" to referrer)
                        )
                    }
                },
                "mqifun" to { _, _, _ -> throw RuntimeException("不可用的播放源") }
            )
    }
}