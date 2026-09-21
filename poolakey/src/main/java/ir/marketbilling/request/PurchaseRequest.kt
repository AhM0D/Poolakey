package ir.marketbilling.request

import android.os.Bundle
import ir.marketbilling.constant.MarketIntent

data class PurchaseRequest(
    val productId: String,
    val payload: String? = null,
    val dynamicPriceToken: String? = null
) {

    internal var cutoutModeIsShortEdges = false
}

internal fun PurchaseRequest.purchaseExtraData(): Bundle {
    return Bundle().apply {
        putString(MarketIntent.RESPONSE_DYNAMIC_PRICE_TOKEN, dynamicPriceToken)
        putBoolean(MarketIntent.RESPONSE_CUTOUT_MODE_IS_SHORT_EDGES, cutoutModeIsShortEdges)
    }
}