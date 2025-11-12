package io.github.remote.konfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverrideStoreTest {
    @Test
    fun storesAndRetrievesOverrides() {
        val store = OverrideStore()
        assertNull(store.get("missing"))

        store.put("welcome", "{\"message\":\"hi\"}")
        assertEquals("{\"message\":\"hi\"}", store.get("welcome"))

        store.remove("welcome")
        assertNull(store.get("welcome"))

        store.setOverride("welcome", "value")
        assertEquals("value", store.getOverride("welcome"))

        store.clearOverride("welcome")
        assertNull(store.get("welcome"))

        store.put("welcome", "value")
        store.clear()
        assertNull(store.get("welcome"))
    }

    @Test
    fun disabledStoreIgnoresOverrides() {
        val store = OverrideStore(developmentMode = false)

        store.put("welcome", "ignored")
        assertNull(store.get("welcome"))

        store.setOverride("welcome", "ignored")
        assertNull(store.getOverride("welcome"))

        store.remove("welcome")
        store.clearOverride("welcome")
        store.clear()
    }
}
