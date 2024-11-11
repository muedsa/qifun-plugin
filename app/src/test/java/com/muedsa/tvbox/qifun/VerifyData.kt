package com.muedsa.tvbox.qifun

import kotlinx.serialization.Serializable

@Serializable
data class VerifyData(
    var result: Int,
    var horizontalProjection: List<Int>,
    var verticalProjection: List<Int>,
    var imgStr: String
)
