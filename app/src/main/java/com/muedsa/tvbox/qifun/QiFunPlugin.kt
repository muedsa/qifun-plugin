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
import com.muedsa.tvbox.tool.PluginCookieStore
import com.muedsa.tvbox.tool.SharedCookieSaver

class QiFunPlugin(tvBoxContext: TvBoxContext) : IPlugin(tvBoxContext = tvBoxContext) {
    private val cookieStore by lazy {
        PluginCookieStore(
            saver = SharedCookieSaver(
                store = tvBoxContext.store
            ).apply { save(QiFunConsts.COOKIE_PHPSESSID) }
        )
    }
    private val mainScreenService by lazy { MainScreenService(cookieStore = cookieStore) }
    private val mediaDetailService by lazy { MediaDetailService(cookieStore = cookieStore) }
    private val mediaSearchService by lazy { MediaSearchService(cookieStore = cookieStore) }

    override fun provideMainScreenService(): IMainScreenService = mainScreenService
    override fun provideMediaDetailService(): IMediaDetailService = mediaDetailService
    override fun provideMediaSearchService(): IMediaSearchService = mediaSearchService

    override suspend fun onInit() {}
    override suspend fun onLaunched() {}
    override var options: PluginOptions = PluginOptions(enableDanDanPlaySearch = true)
}