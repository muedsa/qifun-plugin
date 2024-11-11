package com.muedsa.tvbox.qifun

import com.muedsa.tvbox.api.plugin.IPlugin
import com.muedsa.tvbox.api.plugin.PluginOptions
import com.muedsa.tvbox.api.plugin.TvBoxContext
import com.muedsa.tvbox.api.service.IMainScreenService
import com.muedsa.tvbox.api.service.IMediaDetailService
import com.muedsa.tvbox.api.service.IMediaSearchService
import com.muedsa.tvbox.qifun.service.MainScreenService
import com.muedsa.tvbox.qifun.service.MediaDetailService
import com.muedsa.tvbox.qifun.service.MediaSearchService
import com.muedsa.tvbox.qifun.service.VerifyService
import com.muedsa.tvbox.tool.PluginCookieJar
import com.muedsa.tvbox.tool.PluginCookieStore
import com.muedsa.tvbox.tool.SharedCookieSaver
import com.muedsa.tvbox.tool.createOkHttpClient

class QiFunPlugin(tvBoxContext: TvBoxContext) : IPlugin(tvBoxContext = tvBoxContext) {

    private val cookieSaver by lazy {
        SharedCookieSaver(
            store = tvBoxContext.store
        )
    }
    private val cookieStore by lazy { PluginCookieStore(saver = cookieSaver) }
    private val okHttpClient by lazy {
        createOkHttpClient(
            debug = tvBoxContext.debug,
            cookieJar = PluginCookieJar(saver = cookieSaver)
        )
    }
    private val verifyService by lazy { VerifyService(okHttpClient = okHttpClient) }
    private val mainScreenService by lazy { MainScreenService(cookieStore = cookieStore) }
    private val mediaDetailService by lazy { MediaDetailService(cookieStore = cookieStore) }
    private val mediaSearchService by lazy {
        MediaSearchService(
            verifyService = verifyService,
            okHttpClient = okHttpClient
        )
    }

    override fun provideMainScreenService(): IMainScreenService = mainScreenService
    override fun provideMediaDetailService(): IMediaDetailService = mediaDetailService
    override fun provideMediaSearchService(): IMediaSearchService = mediaSearchService

    override suspend fun onInit() {}
    override suspend fun onLaunched() {}
    override var options: PluginOptions = PluginOptions(enableDanDanPlaySearch = true)
}