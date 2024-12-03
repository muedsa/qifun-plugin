package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCardType
import com.muedsa.tvbox.api.data.MediaEpisode
import com.muedsa.tvbox.api.data.MediaPlaySource
import com.muedsa.tvbox.qifun.QiFunConsts
import com.muedsa.tvbox.qifun.TestPlugin
import com.muedsa.tvbox.qifun.checkMediaCard
import com.muedsa.tvbox.qifun.checkMediaCardRow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaDetailServiceTest {

    private val service = TestPlugin.provideMediaDetailService()

    @Test
    fun getDetailData_test() = runTest{
        val detail = service.getDetailData("/voddetail/2459.html", "/voddetail/2459.html")
        check(detail.id.isNotEmpty())
        check(detail.title.isNotEmpty())
        check(detail.detailUrl.isNotEmpty())
        check(detail.backgroundImageUrl.isNotEmpty())
        detail.favoritedMediaCard?.let { favoritedMediaCard ->
            checkMediaCard(favoritedMediaCard, cardType = MediaCardType.STANDARD)
            check(favoritedMediaCard.cardWidth > 0)
            check(favoritedMediaCard.cardHeight > 0)
        }
        check(detail.playSourceList.isNotEmpty())
        detail.playSourceList.forEach { mediaPlaySource ->
            check(mediaPlaySource.id.isNotEmpty())
            check(mediaPlaySource.name.isNotEmpty())
            check(mediaPlaySource.episodeList.isNotEmpty())
            mediaPlaySource.episodeList.forEach {
                check(it.id.isNotEmpty())
                check(it.name.isNotEmpty())
            }
        }
        detail.rows.forEach {
            checkMediaCardRow(it)
        }
    }

    @Test
    fun getEpisodePlayInfo_test() = runTest{
        val mediaPlaySource = MediaPlaySource(
            id = "",
            name = "",
            episodeList = listOf()
        )
        val mediaEpisode = MediaEpisode(
            id = "",
            name = "",
            flag5 = "/vodplay/5043-3-1.html",
            flag6 = "${QiFunConsts.SITE_URL}/voddetail/5043.html"
        )
        val playInfo = service.getEpisodePlayInfo(mediaPlaySource, mediaEpisode)
        check(playInfo.url.isNotEmpty())
    }
}