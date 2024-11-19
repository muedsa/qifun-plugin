package com.muedsa.tvbox.qifun

import com.muedsa.tvbox.api.plugin.IPlugin
import com.muedsa.tvbox.api.plugin.PluginOptions
import com.muedsa.tvbox.api.plugin.TvBoxContext
import com.muedsa.tvbox.api.service.IMainScreenService
import com.muedsa.tvbox.api.service.IMediaCatalogService
import com.muedsa.tvbox.api.service.IMediaDetailService
import com.muedsa.tvbox.api.service.IMediaSearchService
import com.muedsa.tvbox.qifun.service.MainScreenService
import com.muedsa.tvbox.qifun.service.MediaCatalogService
import com.muedsa.tvbox.qifun.service.MediaDetailService
import com.muedsa.tvbox.qifun.service.MediaSearchService
import com.muedsa.tvbox.qifun.service.VerifyService
import com.muedsa.tvbox.tool.IPv6Checker
import com.muedsa.tvbox.tool.PluginCookieJar
import com.muedsa.tvbox.tool.SharedCookieSaver
import com.muedsa.tvbox.tool.createOkHttpClient
import java.util.concurrent.TimeUnit

class QiFunPlugin(tvBoxContext: TvBoxContext) : IPlugin(tvBoxContext = tvBoxContext) {

    private val okHttpClient by lazy {
        createOkHttpClient(
            debug = tvBoxContext.debug,
            cookieJar = PluginCookieJar(
                saver = SharedCookieSaver(store = tvBoxContext.store)
            ),
            onlyIpv4 = tvBoxContext.iPv6Status != IPv6Checker.IPv6Status.SUPPORTED
        ) {
            callTimeout(40, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
        }
    }
    private val verifyService by lazy { VerifyService(okHttpClient = okHttpClient) }
    private val mainScreenService by lazy { MainScreenService(okHttpClient = okHttpClient) }
    private val mediaDetailService by lazy { MediaDetailService(okHttpClient = okHttpClient) }
    private val mediaSearchService by lazy {
        MediaSearchService(
            verifyService = verifyService,
            okHttpClient = okHttpClient
        )
    }
    private val mediaCatalogService by lazy {
        MediaCatalogService(
            verifyService = verifyService,
            okHttpClient = okHttpClient
        )
    }

    override fun provideMainScreenService(): IMainScreenService = mainScreenService
    override fun provideMediaDetailService(): IMediaDetailService = mediaDetailService
    override fun provideMediaSearchService(): IMediaSearchService = mediaSearchService
    override fun provideMediaCatalogService(): IMediaCatalogService = mediaCatalogService

    override suspend fun onInit() {}
    override suspend fun onLaunched() {}
    override var options: PluginOptions = PluginOptions(enableDanDanPlaySearch = true)
}