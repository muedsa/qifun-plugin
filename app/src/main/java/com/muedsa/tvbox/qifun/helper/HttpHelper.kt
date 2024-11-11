package com.muedsa.tvbox.qifun.helper

import com.muedsa.tvbox.tool.ChromeUserAgent
import okhttp3.CacheControl
import okhttp3.Request

fun Request.Builder.feignChrome(referrer: String? = null): Request.Builder {
    return addHeader("User-Agent", ChromeUserAgent)
        .apply {
            if(!referrer.isNullOrEmpty()) {
                addHeader("Referrer", referrer)
            }
        }
        .cacheControl(CacheControl.FORCE_NETWORK)
        .addHeader("Pragma", "no-cache")
        .addHeader("Priority", "u=0, i")
        .addHeader("Sec-Ch-Ua", "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\"")
        .addHeader("Sec-Ch-Ua-Platform", "\"Windows\"")
        .addHeader("Upgrade-Insecure-Requests", "1")
        .addHeader("Connection", "close")
}