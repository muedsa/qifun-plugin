package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.api.data.MediaCardType
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
        checkMediaCard(detail.favoritedMediaCard, cardType = MediaCardType.STANDARD)
        check(detail.favoritedMediaCard.cardWidth > 0)
        check(detail.favoritedMediaCard.cardHeight > 0)
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
        val detail = service.getDetailData("/voddetail/2459.html", "/voddetail/2459.html")
        check(detail.playSourceList.isNotEmpty())
        check(detail.playSourceList.flatMap { it.episodeList }.isNotEmpty())
        val mediaPlaySource = detail.playSourceList[0]
        val mediaEpisode = mediaPlaySource.episodeList[0]
        val playInfo = service.getEpisodePlayInfo(mediaPlaySource, mediaEpisode)
        check(playInfo.url.isNotEmpty())
    }

    @Test
    fun dm295DecodeUrl_test()  {
        val deUrl = "AnA3AwVxeAV6SQobPA9yUXxheVc4WWISU1NUFgBlfg0HfzNYAn4MSXpdfRcENg4VaVtYTwZzDhx8NnEQAGp6Vi9gP18qbXREeGR1Cyshdht8bnlXLgYOVVE2T1AASHZIMWABXDQIY3xhW3UrMDN1OWR+RwsxcHEsYjR9OjACcmoCVREFMVJwHFIDV1IqC3ETalhhTC5ZeQw="
        val url = MediaDetailService.dm295DecodeUrl(deUrl)
        check(url == "https://sf16-cgfe-sg.ibytedtos.com/obj/tos-alisg-ve-0051c001-sg/owilwk0iEwhGUNZAIUPYLzvH1IEBXCAQjkzZA?www.qifun.cc")
    }
}