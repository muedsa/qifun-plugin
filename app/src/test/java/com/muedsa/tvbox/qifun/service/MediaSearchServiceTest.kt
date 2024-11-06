package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.qifun.TestPlugin
import com.muedsa.tvbox.qifun.checkMediaCardRow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaSearchServiceTest {

    private val service = TestPlugin.provideMediaSearchService()

    @Test
    fun searchMedias_test() = runTest {
        val row = service.searchMedias("哭泣少女乐队")
        checkMediaCardRow(row = row)
    }
}