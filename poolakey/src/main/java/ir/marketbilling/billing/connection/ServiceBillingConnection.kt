package ir.marketbilling.billing.connection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import androidx.activity.result.IntentSenderRequest
import com.android.vending.billing.IInAppBillingService
import ir.marketbilling.ConnectionState
import ir.marketbilling.PurchaseType
import ir.marketbilling.PaymentLauncher
import ir.marketbilling.billing.consume.ConsumeFunction
import ir.marketbilling.billing.consume.ConsumeFunctionRequest
import ir.marketbilling.billing.purchase.PurchaseFunction
import ir.marketbilling.billing.purchase.PurchaseFunctionRequest
import ir.marketbilling.billing.query.QueryFunction
import ir.marketbilling.billing.query.QueryFunctionRequest
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
import ir.marketbilling.constant.MarketIntent
import ir.marketbilling.constant.Billing
import ir.marketbilling.constant.MarketConfig
import ir.marketbilling.exception.MarketNotFoundException
import ir.marketbilling.exception.DisconnectException
import ir.marketbilling.exception.IAPNotSupportedException
import ir.marketbilling.exception.SubsNotSupportedException
import ir.marketbilling.request.PurchaseRequest
import ir.marketbilling.security.Security
import ir.marketbilling.thread.PoolakeyThread
import java.lang.ref.WeakReference

internal class ServiceBillingConnection(
    private val context: Context,
    mainThread: PoolakeyThread<() -> Unit>,
    private val backgroundThread: PoolakeyThread<Runnable>,
    private val paymentConfiguration: PaymentConfiguration,
    private val queryFunction: QueryFunction,
    private val getSkuDetailFunction: GetSkuDetailFunction,
    private val checkTrialSubscriptionFunction: CheckTrialSubscriptionFunction,
    private val onServiceDisconnected: () -> Unit
) : BillingConnectionCommunicator, ServiceConnection {

    private val purchaseFunction = PurchaseFunction(context)

    private val marketConfig = MarketConfig.from(context)

    private val consumeFunction = ConsumeFunction(mainThread, context)

    private var billingService: IInAppBillingService? = null
    private var callbackReference: WeakReference<ConnectionCallback>? = null
    private var contextReference: WeakReference<Context>? = null

    override fun startConnection(
        context: Context,
        callback: ConnectionCallback
    ): ConnectionResult {
        callbackReference = WeakReference(callback)
        contextReference = WeakReference(context)

        return Intent(marketConfig.bindAddress).apply {
            `package` = marketConfig.packageName
        }.let {
            if (Security.verifyMarketIsInstalled(context, marketConfig) && isServiceAvailable(it)) {
                try {
                    context.bindService(it, this, Context.BIND_AUTO_CREATE)
                    ConnectionResult.Success
                } catch (e: SecurityException) {
                    ConnectionResult.Failed(e)
                }
            } else {
                return ConnectionResult.Failed(MarketNotFoundException())
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        IInAppBillingService.Stub.asInterface(service)
            ?.also { billingService = it }
            ?.also {
                try {
                    if (isPurchaseTypeSupported(purchaseType = PurchaseType.IN_APP)) {
                        if (!paymentConfiguration.shouldSupportSubscription || isPurchaseTypeSupported(
                                purchaseType = PurchaseType.SUBSCRIPTION
                            )
                        ) {
                            callbackReference?.get()?.connectionSucceed?.invoke()
                        } else {
                            callbackReference?.get()?.connectionFailed?.invoke(
                                SubsNotSupportedException()
                            )
                        }
                    } else {
                        callbackReference?.get()?.connectionFailed?.invoke(IAPNotSupportedException())
                    }
                } catch (exception: RemoteException) {
                    callbackReference?.get()?.connectionFailed?.invoke(exception)
                }
            }
    }

    private fun isPurchaseTypeSupported(purchaseType: PurchaseType): Boolean {
        return contextReference?.get()?.let { context ->
            val supportState = billingService?.isBillingSupported(
                Billing.IN_APP_BILLING_VERSION,
                context.packageName,
                purchaseType.type
            )

            supportState == MarketIntent.RESPONSE_RESULT_OK
        } ?: false
    }

    override fun consume(
        purchaseToken: String,
        callback: ConsumeCallback.() -> Unit
    ) = withService(runOnBackground = true) {
        consumeFunction.function(
            billingService = this,
            request = ConsumeFunctionRequest(purchaseToken, callback)
        )
    } ifServiceIsDisconnected {
        ConsumeCallback().apply(callback).consumeFailed.invoke(DisconnectException())
    }

    override fun queryPurchasedProducts(
        purchaseType: PurchaseType,
        callback: PurchaseQueryCallback.() -> Unit
    ) = withService(runOnBackground = true) {
        queryFunction.function(
            request = QueryFunctionRequest(
                purchaseType.type,
                ::getQueryPurchasedBundle,
                callback
            )
        )
    } ifServiceIsDisconnected {
        PurchaseQueryCallback().apply(callback).queryFailed.invoke(DisconnectException())
    }

    override fun purchase(
        paymentLauncher: PaymentLauncher,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit,
    ) {

        val intentSenderFire: (IntentSender) -> Unit = { intentSender ->
            paymentLauncher.intentSenderLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build()
            )
            PurchaseCallback().apply(callback).purchaseFlowBegan.invoke()
        }

        val intentFire: (Intent) -> Unit = { intent ->
            paymentLauncher.activityLauncher.launch(intent)
            PurchaseCallback().apply(callback).purchaseFlowBegan.invoke()
        }

        purchase(
            purchaseRequest,
            purchaseType,
            callback,
            intentSenderFire,
            intentFire
        )
    }

    override fun getSkuDetails(
        request: SkuDetailFunctionRequest,
        callback: GetSkuDetailsCallback.() -> Unit
    ) = withService(runOnBackground = true) {
        getSkuDetailFunction.function(
            billingService = this,
            request = request
        )
    } ifServiceIsDisconnected {
        GetSkuDetailsCallback().apply(callback).getSkuDetailsFailed.invoke(DisconnectException())
    }

    override fun checkTrialSubscription(
        request: CheckTrialSubscriptionFunctionRequest,
        callback: CheckTrialSubscriptionCallback.() -> Unit
    ) = withService(runOnBackground = true) {
        checkTrialSubscriptionFunction.function(
            billingService = this,
            request = request
        )
    } ifServiceIsDisconnected {
        CheckTrialSubscriptionCallback().apply(callback).checkTrialSubscriptionFailed.invoke(
            DisconnectException()
        )
    }

    private fun purchase(
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit,
        fireIntentSender: (IntentSender) -> Unit,
        fireIntent: (Intent) -> Unit
    ) = withService {
        purchaseFunction.function(
            billingService = this,
            request = PurchaseFunctionRequest(
                purchaseRequest,
                purchaseType,
                callback,
                fireIntentSender,
                fireIntent
            )
        )
    } ifServiceIsDisconnected {
        PurchaseCallback().apply(callback).failedToBeginFlow.invoke(DisconnectException())
    }

    private fun getQueryPurchasedBundle(
        purchaseType: String,
        continuation: String?
    ): Bundle? {
        return billingService?.getPurchases(
            Billing.IN_APP_BILLING_VERSION,
            context.packageName,
            purchaseType,
            continuation
        )
    }

    private inline fun withService(
        runOnBackground: Boolean = false,
        crossinline service: IInAppBillingService.() -> Unit
    ): ConnectionState {
        return billingService?.also {
            if (runOnBackground) {
                backgroundThread.execute(Runnable { service.invoke(it) })
            } else {
                service.invoke(it)
            }
        }?.let { ConnectionState.Connected }
            ?: run { ConnectionState.Disconnected }
    }

    override fun stopConnection() {
        if (billingService != null) {
            contextReference?.get()?.unbindService(this)
            disconnect()
        }
    }

    private inline infix fun ConnectionState.ifServiceIsDisconnected(block: () -> Unit) {
        if (this is ConnectionState.Disconnected) {
            block.invoke()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        disconnect()
        onServiceDisconnected.invoke()
    }

    private fun disconnect() {
        billingService = null
    }

    private fun isServiceAvailable(intent: Intent): Boolean {
        return context.packageManager.queryIntentServices(intent, 0).isNotEmpty() ||
                isServiceAvailableInDeepSleep(intent)
    }

    private fun isServiceAvailableInDeepSleep(intent: Intent): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                context.packageManager
                    .queryIntentServices(intent, MATCH_DISABLED_COMPONENTS)
                    .isNotEmpty()
    }

    companion object
}