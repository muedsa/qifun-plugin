package com.muedsa.tvbox.qifun.service

import com.muedsa.tvbox.qifun.TestPlugin
import com.muedsa.tvbox.qifun.checkMediaCardRow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaSearchServiceTest {

    private val service = TestPlugin.provideMediaSearchService()

    @Test
    fun searchMedias_test() = runTest {
        val row = service.searchMedias("哭泣少女乐队")
        checkMediaCardRow(row = row)
    }

    init {
        System.setProperty("javax.net.debug", "all")
    }
}