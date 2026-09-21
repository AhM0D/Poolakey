package ir.cafebazaar.poolakey.constant

import ir.cafebazaar.poolakey.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketConfigTest {

    @Test
    fun `resolve falls back to Bazaar when meta-data is absent`() {
        val config = MarketConfig.resolve(marketId = null, bindAddress = null)

        assertEquals("com.farsitel.bazaar", config.packageName)
        assertEquals("ir.cafebazaar.pardakht.InAppBillingService.BIND", config.bindAddress)
        assertEquals(BuildConfig.BAZAAR_HASH, config.signatureHash)
        assertEquals(801301L, config.receiverConnectionMinVersion)
    }

    @Test
    fun `resolve pins Bazaar and keeps its receiver gate`() {
        val config = MarketConfig.resolve(
            marketId = "com.farsitel.bazaar",
            bindAddress = "ir.cafebazaar.pardakht.InAppBillingService.BIND"
        )

        assertEquals(BuildConfig.BAZAAR_HASH, config.signatureHash)
        assertEquals(801301L, config.receiverConnectionMinVersion)
    }

    @Test
    fun `resolve pins Myket and disables the receiver path`() {
        val config = MarketConfig.resolve(
            marketId = "ir.mservices.market",
            bindAddress = "ir.mservices.market.InAppBillingService.BIND"
        )

        assertEquals("ir.mservices.market", config.packageName)
        assertEquals("ir.mservices.market.InAppBillingService.BIND", config.bindAddress)
        assertEquals(BuildConfig.MYKET_HASH, config.signatureHash)
        assertEquals(Long.MAX_VALUE, config.receiverConnectionMinVersion)
    }

    @Test
    fun `resolve refuses an unknown market rather than leaving it unpinned`() {
        val config = MarketConfig.resolve(
            marketId = "com.example.rogue",
            bindAddress = "com.example.rogue.Billing.BIND"
        )

        assertNull(config.signatureHash)
        assertEquals(Long.MAX_VALUE, config.receiverConnectionMinVersion)
    }

    @Test
    fun `resolve ignores a blank market id`() {
        val config = MarketConfig.resolve(marketId = "", bindAddress = "")

        assertEquals("com.farsitel.bazaar", config.packageName)
        assertEquals(BuildConfig.BAZAAR_HASH, config.signatureHash)
    }
}
