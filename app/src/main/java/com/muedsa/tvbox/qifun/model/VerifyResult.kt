package com.muedsa.tvbox.qifun.model

import kotlinx.serialization.Serializable

@Serializable
data class VerifyResult(
    val code: Int = -1,
    val msg: String = ""
)