package com.muedsa.tvbox.qifun

import com.muedsa.tvbox.tool.SerializableCookie

object QiFunConsts {
    const val SITE_URL = "https://www.qifun.cc"
    const val CARD_WIDTH = 104
    const val CARD_HEIGHT = 147

    val COOKIE_PHPSESSID = SerializableCookie(
        name = "PHPSESSID",
        value = "jvlhbaqpqthje0pk6emit7di9q",
        expiresAt = Long.MAX_VALUE,
        path = "/",
        domain = "www.qifun.cc",
        secure = false,
        httpOnly = false,
        persistent = true,
        hostOnly = false
    )
}