package com.apexpredator.argus.update

import com.apexpredator.argus.data.parseUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    @Test
    fun parsesVersionJson() {
        val info = parseUpdateInfo(
            """{"versionCode":3,"versionName":"1.2.0","apkUrl":"https://example.com/a.apk"}"""
        )
        assertEquals(3, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals("https://example.com/a.apk", info.apkUrl)
    }

    @Test
    fun newerVersionCodeMeansUpdateAvailable() {
        val installed = 2
        val info = parseUpdateInfo(
            """{"versionCode":3,"versionName":"1.2.0","apkUrl":"https://example.com/a.apk"}"""
        )
        assertTrue(info.versionCode > installed)
    }

    @Test(expected = org.json.JSONException::class)
    fun malformedJsonThrows() {
        parseUpdateInfo("""{"versionCode":"not-a-number"}""")
    }
}
