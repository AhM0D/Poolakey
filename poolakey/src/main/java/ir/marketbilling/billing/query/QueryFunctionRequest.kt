package ir.marketbilling.billing.query

import android.os.Bundle
import ir.marketbilling.billing.FunctionRequest
import ir.marketbilling.callback.PurchaseQueryCallback

internal class QueryFunctionRequest(
    val purchaseType: String,
    val queryBundle: (purchaseType: String, continuation: String?) -> Bundle?,
    val callback: PurchaseQueryCallback.() -> Unit
) : FunctionRequest