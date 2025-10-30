package io.github.remote.konfig.sample

import org.junit.Test
import kotlin.test.assertEquals

class AppSmokeTest {
    @Test
    fun helloWorld() {
        assertEquals("Hello, Remote Konfig!".lowercase(), "hello, remote konfig!")
    }
}
