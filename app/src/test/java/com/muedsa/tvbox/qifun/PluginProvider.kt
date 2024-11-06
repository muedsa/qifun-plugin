package com.muedsa.tvbox.qifun

import com.muedsa.tvbox.api.plugin.TvBoxContext

val TestPlugin by lazy {
    QiFunPlugin(
        tvBoxContext = TvBoxContext(
            screenWidth = 1920,
            screenHeight = 1080,
            debug = true,
            store = FakePluginPrefStore()
        )
    )
}