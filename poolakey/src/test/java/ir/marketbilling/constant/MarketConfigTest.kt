package ir.marketbilling.constant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketConfigTest {

    @Test
    fun `resolve carries every supplied value through unchanged`() {
        val config = MarketConfig.resolve(
            marketId = "com.example.store",
            bindAddress = "com.example.store.BIND",
            hash = "AA:BB",
            receiverClass = "com.example.store.Receiver",
            receiverMinVersion = "42",
            featureConfigMinVersion = "99",
            supportsSubscription = "true",
            supportsTrial = "false"
        )

        assertEquals("com.example.store", config.packageName)
        assertEquals("com.example.store.BIND", config.bindAddress)
        assertEquals("AA:BB", config.signatureHash)
        assertEquals("com.example.store.Receiver", config.receiverComponentName)
        assertEquals(42L, config.receiverConnectionMinVersion)
        assertEquals(99L, config.featureConfigMinVersion)
        assertEquals(true, config.supportsSubscription)
        assertEquals(false, config.supportsTrialSubscription)
    }

    @Test
    fun `a missing hash refuses the connection`() {
        val config = MarketConfig.resolve(
            marketId = "com.example.store", bindAddress = "x", hash = null,
            receiverClass = null, receiverMinVersion = "1", featureConfigMinVersion = "1",
            supportsSubscription = "true", supportsTrial = "true"
        )
        assertNull(config.signatureHash)
    }

    @Test
    fun `a blank receiver class means no explicit component`() {
        val config = MarketConfig.resolve(
            marketId = "s", bindAddress = "b", hash = "h", receiverClass = "  ",
            receiverMinVersion = "1", featureConfigMinVersion = "1",
            supportsSubscription = "true", supportsTrial = "true"
        )
        assertNull(config.receiverComponentName)
    }

    @Test
    fun `an unparseable version disables that transport rather than crashing`() {
        val config = MarketConfig.resolve(
            marketId = "s", bindAddress = "b", hash = "h", receiverClass = null,
            receiverMinVersion = "not-a-number", featureConfigMinVersion = "",
            supportsSubscription = "true", supportsTrial = "true"
        )
        assertEquals(Long.MAX_VALUE, config.receiverConnectionMinVersion)
        assertEquals(Long.MAX_VALUE, config.featureConfigMinVersion)
    }

    @Test
    fun `capability flags default to false when absent`() {
        val config = MarketConfig.resolve(
            marketId = "s", bindAddress = "b", hash = "h", receiverClass = null,
            receiverMinVersion = "1", featureConfigMinVersion = "1",
            supportsSubscription = null, supportsTrial = null
        )
        assertEquals(false, config.supportsSubscription)
        assertEquals(false, config.supportsTrialSubscription)
    }
}
