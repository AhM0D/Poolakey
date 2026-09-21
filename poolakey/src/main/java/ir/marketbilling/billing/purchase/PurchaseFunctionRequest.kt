package ir.marketbilling.billing.purchase

import android.content.Intent
import android.content.IntentSender
import ir.marketbilling.PurchaseType
import ir.marketbilling.billing.FunctionRequest
import ir.marketbilling.callback.PurchaseCallback
import ir.marketbilling.request.PurchaseRequest

internal class PurchaseFunctionRequest(
    val purchaseRequest: PurchaseRequest,
    val purchaseType: PurchaseType,
    val callback: PurchaseCallback.() -> Unit,
    val launchIntentWithIntentSender: (IntentSender) -> Unit,
    val launchIntent: (Intent) -> Unit
) : FunctionRequest