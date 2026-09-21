package ir.marketbilling.billing.consume

import ir.marketbilling.billing.FunctionRequest
import ir.marketbilling.callback.ConsumeCallback

internal class ConsumeFunctionRequest(
    val purchaseToken: String,
    val callback: ConsumeCallback.() -> Unit
): FunctionRequest