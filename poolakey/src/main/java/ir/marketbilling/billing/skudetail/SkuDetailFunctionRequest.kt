package ir.marketbilling.billing.skudetail

import ir.marketbilling.PurchaseType
import ir.marketbilling.billing.FunctionRequest
import ir.marketbilling.callback.GetSkuDetailsCallback

internal class SkuDetailFunctionRequest(
    val purchaseType: PurchaseType,
    val skuIds: List<String>,
    val callback: GetSkuDetailsCallback.() -> Unit
) : FunctionRequest