package com.muedsa.tvbox.qifun

import com.muedsa.tvbox.qifun.service.MediaDetailService
import com.muedsa.tvbox.tool.LenientJson
import com.muedsa.tvbox.tool.createOkHttpClient
import com.muedsa.tvbox.tool.feignChrome
import com.muedsa.tvbox.tool.get
import com.muedsa.tvbox.tool.stringBody
import com.muedsa.tvbox.tool.toRequestBuild
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.intArrayOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UrlContentValidator {

    private val okHttpClient = createOkHttpClient()

    @Test
    fun parse_js_test() {
        val content = "${QiFunConsts.SITE_URL}/static/player/parse.js".toRequestBuild()
            .feignChrome(referer = QiFunConsts.SITE_URL)
            .get(okHttpClient = okHttpClient)
            .stringBody()
        check(content.contains("src=\"'+MacPlayer.Parse + MacPlayer.PlayUrl+'\"")) {
            "check parse.js fail, content:\n$content"
        }
    }

    @Test
    fun player_config_js_test() {
        val content = "${QiFunConsts.SITE_URL}/static/js/playerconfig.js".toRequestBuild()
            .feignChrome(referer = QiFunConsts.SITE_URL)
            .get(okHttpClient = okHttpClient)
            .stringBody()
        val json = PLAYER_LIST_REGEX.find(content)?.groups[1]?.value
            ?: throw RuntimeException("PLAYER_LIST_REGEX fail")
        val configMap = LenientJson.decodeFromString<Map<String, Map<String, String>>>(json)
            .filter { !IGNORE_PLAYERS.contains(it.key) && !it.value["parse"].isNullOrBlank() }
        for ((player, config) in configMap) {
            var parse = config["parse"]
            if (!parse.isNullOrBlank()) {
                var url = MediaDetailService.PARSER_URL_MAP[player]
                check(!url.isNullOrBlank()) { "有新的播放源: $player: $config" }
                url = url.replaceAfter("?", "")
                    .removeSuffix("?")
                    .removeSuffix("/")
                parse = parse.replaceAfter("?", "")
                    .removeSuffix("?")
                    .removeSuffix("/")
                check(url.contains(parse)) { "更新播放源: $player: $config" }
            }
        }
    }

    companion object {
        val PLAYER_LIST_REGEX = "MacPlayerConfig\\.player_list=(\\{.*?\\}),MacPlayerConfig".toRegex()
        val IGNORE_PLAYERS = listOf("dplayer", "videojs", "iva", "iframe", "link", "swf", "flv")
    }
}