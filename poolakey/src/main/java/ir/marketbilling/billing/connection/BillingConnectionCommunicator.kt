package ir.marketbilling.billing.connection

import android.content.Context
import ir.marketbilling.PurchaseType
import ir.marketbilling.PaymentLauncher
import ir.marketbilling.billing.skudetail.SkuDetailFunctionRequest
import ir.marketbilling.billing.trialsubscription.CheckTrialSubscriptionFunctionRequest
import ir.marketbilling.callback.CheckTrialSubscriptionCallback
import ir.marketbilling.callback.ConnectionCallback
import ir.marketbilling.callback.ConsumeCallback
import ir.marketbilling.callback.GetSkuDetailsCallback
import ir.marketbilling.callback.PurchaseCallback
import ir.marketbilling.callback.PurchaseQueryCallback
import ir.marketbilling.request.PurchaseRequest

internal interface BillingConnectionCommunicator {

    fun startConnection(
        context: Context,
        callback: ConnectionCallback
    ): ConnectionResult

    fun consume(
        purchaseToken: String,
        callback: ConsumeCallback.() -> Unit
    )

    fun queryPurchasedProducts(
        purchaseType: PurchaseType,
        callback: PurchaseQueryCallback.() -> Unit
    )

    fun purchase(
        paymentLauncher: PaymentLauncher,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit
    )

    fun getSkuDetails(
        request: SkuDetailFunctionRequest,
        callback: GetSkuDetailsCallback.() -> Unit,
    )

    fun checkTrialSubscription(
        request: CheckTrialSubscriptionFunctionRequest,
        callback: CheckTrialSubscriptionCallback.() -> Unit,
    )

    fun stopConnection()
}
