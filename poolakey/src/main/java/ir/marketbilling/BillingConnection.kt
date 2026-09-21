package ir.marketbilling

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import ir.marketbilling.billing.connection.BillingConnectionCommunicator
import ir.marketbilling.billing.connection.ConnectionResult
import ir.marketbilling.billing.connection.ReceiverBillingConnection
import ir.marketbilling.billing.connection.ServiceBillingConnection
import ir.marketbilling.billing.query.QueryFunction
import ir.marketbilling.billing.skudetail.GetSkuDetailFunction
import ir.marketbilling.billing.skudetail.SkuDetailFunctionRequest
import ir.marketbilling.billing.trialsubscription.CheckTrialSubscriptionFunction
import ir.marketbilling.billing.trialsubscription.CheckTrialSubscriptionFunctionRequest
import ir.marketbilling.callback.CheckTrialSubscriptionCallback
import ir.marketbilling.callback.ConnectionCallback
import ir.marketbilling.callback.ConsumeCallback
import ir.marketbilling.callback.GetSkuDetailsCallback
import ir.marketbilling.callback.PurchaseCallback
import ir.marketbilling.callback.PurchaseQueryCallback
import ir.marketbilling.config.PaymentConfiguration
import ir.marketbilling.request.PurchaseRequest
import ir.marketbilling.thread.PoolakeyThread

internal class BillingConnection(
    private val context: Context,
    private val paymentConfiguration: PaymentConfiguration,
    private val backgroundThread: PoolakeyThread<Runnable>,
    private val queryFunction: QueryFunction,
    private val skuDetailFunction: GetSkuDetailFunction,
    private val purchaseResultParser: PurchaseResultParser,
    private val checkTrialSubscriptionFunction: CheckTrialSubscriptionFunction,
    private val mainThread: PoolakeyThread<() -> Unit>
) {

    private var callback: ConnectionCallback? = null
    private var paymentLauncher: PaymentLauncher? = null

    private var billingCommunicator: BillingConnectionCommunicator? = null

    internal fun startConnection(connectionCallback: ConnectionCallback.() -> Unit): Connection {
        callback = ConnectionCallback(disconnect = ::stopConnection).apply(connectionCallback)

        val serviceCommunicator = ServiceBillingConnection(
            context,
            mainThread,
            backgroundThread,
            paymentConfiguration,
            queryFunction,
            skuDetailFunction,
            checkTrialSubscriptionFunction,
            ::disconnect
        )

        val receiverConnection = ReceiverBillingConnection(
            paymentConfiguration,
            queryFunction
        )

        billingCommunicator = serviceCommunicator.startConnection(
            context,
            requireNotNull(callback)
        ).let {
            if (it is ConnectionResult.Success) {
                serviceCommunicator
            } else {
                val connectionResult = receiverConnection.startConnection(
                    context,
                    requireNotNull(callback)
                )

                if (connectionResult is ConnectionResult.Failed) {
                    requireNotNull(callback).connectionFailed.invoke(connectionResult.exception)
                }
                receiverConnection
            }
        }

        return requireNotNull(callback)
    }

    fun purchase(
        registry: ActivityResultRegistry,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        purchaseCallback: PurchaseCallback.() -> Unit
    ) {
        paymentLauncher = PaymentLauncher.Builder(registry) {
            onActivityResult(it, purchaseCallback)
        }.build()

        purchaseRequest.cutoutModeIsShortEdges = if (SDK_INT >= Build.VERSION_CODES.P) {
            (context as? Activity)
                ?.window
                ?.attributes
                ?.layoutInDisplayCutoutMode == LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            false
        }

        runOnCommunicator(TAG_PURCHASE) { billingCommunicator ->
            billingCommunicator.purchase(
                requireNotNull(paymentLauncher),
                purchaseRequest,
                purchaseType,
                purchaseCallback
            )
        }
    }

    fun consume(
        purchaseToken: String,
        callback: ConsumeCallback.() -> Unit
    ) {
        runOnCommunicator(TAG_CONSUME) { billingCommunicator ->
            billingCommunicator.consume(
                purchaseToken,
                callback
            )
        }
    }

    fun queryPurchasedProducts(
        purchaseType: PurchaseType,
        callback: PurchaseQueryCallback.() -> Unit
    ) {
        runOnCommunicator(TAG_QUERY_PURCHASE_PRODUCT) { billingCommunicator ->
            billingCommunicator.queryPurchasedProducts(
                purchaseType,
                callback
            )
        }
    }

    fun getSkuDetail(
        purchaseType: PurchaseType,
        skuIds: List<String>,
        callback: GetSkuDetailsCallback.() -> Unit
    ) {
        runOnCommunicator(TAG_GET_SKU_DETAIL) { billingCommunicator ->
            billingCommunicator.getSkuDetails(
                SkuDetailFunctionRequest(purchaseType, skuIds, callback),
                callback
            )
        }
    }

    fun checkTrialSubscription(
        callback: CheckTrialSubscriptionCallback.() -> Unit
    ) {
        runOnCommunicator(TAG_CHECK_TRIAL_SUBSCRIPTION) { billingCommunicator ->
            billingCommunicator.checkTrialSubscription(
                CheckTrialSubscriptionFunctionRequest(callback),
                callback
            )
        }
    }

    private fun stopConnection() {
        runOnCommunicator(TAG_STOP_CONNECTION) { billingCommunicator ->
            billingCommunicator.stopConnection()
            disconnect()
        }
    }

    private fun disconnect() {
        callback?.disconnected?.invoke()
        callback = null
        paymentLauncher?.unregister()
        paymentLauncher = null
        backgroundThread.dispose()
        billingCommunicator = null
    }

    private fun runOnCommunicator(
        methodName: String,
        ifConnected: (BillingConnectionCommunicator) -> Unit
    ) {
        billingCommunicator?.let(ifConnected)
            ?: raiseErrorForCommunicatorNotInitialized(methodName)
    }

    private fun raiseErrorForCommunicatorNotInitialized(methodName: String) {
        callback?.connectionFailed?.invoke(
            IllegalStateException("You called $methodName but communicator is not initialized yet")
        )
    }

    private fun onActivityResult(
        activityResult: ActivityResult,
        purchaseCallback: PurchaseCallback.() -> Unit
    ) {
        when (activityResult.resultCode) {
            Activity.RESULT_OK -> {
                purchaseResultParser.handleReceivedResult(
                    paymentConfiguration.localSecurityCheck,
                    activityResult.data,
                    purchaseCallback
                )
            }
            Activity.RESULT_CANCELED -> {
                PurchaseCallback().apply(purchaseCallback)
                    .purchaseCanceled
                    .invoke()
            }
            else -> {
                PurchaseCallback().apply(purchaseCallback)
                    .purchaseFailed
                    .invoke(IllegalStateException("Result code is not valid"))
            }
        }
    }

    companion object {

        const val PAYMENT_SERVICE_KEY = "payment_service_key"

        private const val TAG_STOP_CONNECTION = "stopConnection"
        private const val TAG_QUERY_PURCHASE_PRODUCT = "queryPurchasedProducts"
        private const val TAG_CONSUME = "consume"
        private const val TAG_PURCHASE = "purchase"
        private const val TAG_GET_SKU_DETAIL = "skuDetial"
        private const val TAG_CHECK_TRIAL_SUBSCRIPTION = "checkTrialSubscription"
    }
}