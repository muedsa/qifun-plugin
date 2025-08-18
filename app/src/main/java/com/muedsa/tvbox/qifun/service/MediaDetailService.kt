package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.DanmakuData
import com.muedsa.tvbox.api.data.DanmakuDataFlow
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
import com.muedsa.tvbox.tool.checkSuccess
import com.muedsa.tvbox.tool.decodeBase64ToStr
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.get
import com.muedsa.tvbox.tool.md5
import com.muedsa.tvbox.tool.parseHtml
import com.muedsa.tvbox.tool.toRequestBuild
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import timber.log.Timber

class MediaDetailService(
    private val okHttpClient: OkHttpClient
) : IMediaDetailService {

    override suspend fun getDetailData(mediaId: String, detailUrl: String): MediaDetail {
        if (!isDetailUrl(detailUrl)) {
            throw RuntimeException("不支持的类型")
        }
        val body = "${QiFunConsts.SITE_URL}$detailUrl".toRequestBuild()
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
        val body = playUrl.toRequestBuild()
            .feignChrome(referer = referrer)
            .get(okHttpClient = okHttpClient)
            .checkSuccess()
            .parseHtml()
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
        Timber.d("解析地址: $playerAAAA")
        return if (playerAAAA.url.endsWith(".m3u8", false)
            || playerAAAA.url.endsWith(".mp4", false)
        ) {
            MediaHttpSource(url = playerAAAA.url, httpHeaders = mapOf("Referrer" to playUrl))
        } else if (PARSER_URL_MAP.containsKey(playerAAAA.from)) {
            step2(playerAAAA = playerAAAA, referrer = playUrl)
        } else {
            MediaHttpSource(url = playerAAAA.url, httpHeaders = mapOf("Referrer" to playUrl))
        }
    }

    private suspend fun step2(playerAAAA: PlayerAAAA, referrer: String): MediaHttpSource {
        delay(200)
        var url = PARSER_URL_MAP[playerAAAA.from]!!
        url = url.replace("{url}", playerAAAA.url)
        var html = url.toRequestBuild()
            .feignChrome(referer = referrer)
            .get(okHttpClient = okHttpClient)
            .checkSuccess()
            .parseHtml()
            .body()
            .html()
        html = decryptHtml(html)
        var playUrl = parseUrl1(html)
        if (playUrl == null) {
            playUrl = parseUrl2(html)
        }
        if (playUrl == null) {
            playUrl = parseUrl3(html)
        }
        if (playUrl == null) {
            playUrl = parseUrl4(html)
        }
        if (playUrl == null) {
            playUrl = parseUrl5(html)
        }
        return MediaHttpSource(
            url = playUrl ?: throw RuntimeException("解析播放源地址失败, $playerAAAA"),
            httpHeaders = mapOf("Referrer" to referrer)
        )
    }

    override suspend fun getEpisodeDanmakuDataList(episode: MediaEpisode): List<DanmakuData> =
        emptyList()

    override suspend fun getEpisodeDanmakuDataFlow(episode: MediaEpisode): DanmakuDataFlow? = null

    companion object {
        fun isDetailUrl(detailUrl: String): Boolean =
            detailUrl.startsWith("${QiFunConsts.SITE_URL}/voddetail")
                    || detailUrl.startsWith("/voddetail")

        private val PLAYER_INFO_REGEX =
            "<script type=\"text/javascript\">var player_aaaa=(\\{.*?\\})</script>".toRegex()

        val PARSER_URL_MAP = mapOf(
            "M" to "${QiFunConsts.SITE_URL}/art/plyr.php?url={url}",
            "qifunqp" to "${QiFunConsts.SITE_URL}/art/plyr.php?url={url}",
            "wxv" to "${QiFunConsts.SITE_URL}/art/wxv.php?url={url}",
            "zhihu" to "${QiFunConsts.SITE_URL}/art/qfzh.php?url={url}",
            "LMM" to "${QiFunConsts.SITE_URL}/art/LMM.php?url={url}",
            "dm295" to "${QiFunConsts.SITE_URL}/art/qf888.php?url={url}",
            "mqifun" to "https://162.14.98.254?vcode={url}",
            "tk" to "${QiFunConsts.SITE_URL}/art/tk6.php?url={url}",
            "ATQP" to "${QiFunConsts.SITE_URL}/art/aut.php?url={url}",
            "lzm3u8" to "${QiFunConsts.SITE_URL}/art/plyr.php?url={url}",
            "heimuer" to "${QiFunConsts.SITE_URL}/art/plyr.php?url={url}",
            "qfyp" to "${QiFunConsts.SITE_URL}/art/qfyp.php?url={url}",
            "vwnet" to "${QiFunConsts.SITE_URL}/art/QINB.php?url={url}",
        )
        private val QIFUNQP_VID_REGEX = "var vid = '(.*?)';".toRegex()
        private val STR_DECODE_URL_REGEX = "strdecode\\('(.*?)'\\),".toRegex()
        private val ATQP_URL_REGEX = "var url = '(.*?\\.m3u8)';".toRegex()
        private val QIFUNQP_URL_REGEX = "var encryptedUrl = '(.*?)';".toRegex()
        @OptIn(ExperimentalStdlibApi::class)
        private val DECODE_KEY = "123456".md5().toHexString().toCharArray()

        private fun decodeUrl(str: String): String {
            val charArr = str.decodeBase64ToStr().toCharArray()
            val strBuilder = StringBuilder()
            charArr.forEachIndexed { index, c ->
                strBuilder.append((c.code xor DECODE_KEY[index % DECODE_KEY.size].code).toChar())
            }
            return strBuilder.toString().decodeBase64ToStr()
        }

        private val ENCRYPTED_HTML_REGEX = "const encryptedHtml = \"([^\"]+)\"".toRegex()
        private val ENC_URL_REGEX = "const encUrl = \"([^\"]+)\"".toRegex()
        fun decrypt(encryptedText: String): String = encryptedText.reversed().decodeBase64ToStr()

        fun decryptHtml(html: String): String {
            var decryptedHtml = html
            val result = ENCRYPTED_HTML_REGEX.find(html)
            result?.groups[1]?.value?.also {
                decryptedHtml = decrypt(it)
            }
            return decryptedHtml
        }

        fun parseUrl1(html: String): String? {
            return ENC_URL_REGEX.find(html)?.let { a ->
                a.groups[1]?.value?.let { b ->
                    decrypt(b)
                }
            }
        }

        fun parseUrl2(html: String): String? {
            return STR_DECODE_URL_REGEX.find(html)?.let { a ->
                a.groups[1]?.value?.let { b ->
                    decodeUrl(b)
                }
            }
        }

        fun parseUrl3(html: String): String? {
            return QIFUNQP_VID_REGEX.find(html)?.let { a ->
                a.groups[1]?.value
            }
        }

        fun parseUrl4(html: String): String? {
            return ATQP_URL_REGEX.find(html)?.let { a ->
                a.groups[1]?.value
            }
        }

        fun parseUrl5(html: String): String? {
            return QIFUNQP_URL_REGEX.find(html)?.let { a ->
                a.groups[1]?.value?.decodeBase64ToStr()
            }
        }
    }
}