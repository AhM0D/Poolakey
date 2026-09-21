package ir.marketbilling.billing.connection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import ir.marketbilling.PurchaseType
import ir.marketbilling.PaymentLauncher
import ir.marketbilling.billing.Feature
import ir.marketbilling.billing.FeatureConfig.isFeatureAvailable
import ir.marketbilling.billing.purchase.PurchaseWeakHolder
import ir.marketbilling.billing.query.QueryFunction
import ir.marketbilling.billing.query.QueryFunctionRequest
import ir.marketbilling.billing.skudetail.SkuDetailFunctionRequest
import ir.marketbilling.billing.skudetail.extractSkuDetailDataFromBundle
import ir.marketbilling.billing.trialsubscription.CheckTrialSubscriptionFunctionRequest
import ir.marketbilling.billing.trialsubscription.extractTrialSubscriptionDataFromBundle
import ir.marketbilling.callback.CheckTrialSubscriptionCallback
import ir.marketbilling.callback.ConnectionCallback
import ir.marketbilling.callback.ConsumeCallback
import ir.marketbilling.callback.GetFeatureConfigCallback
import ir.marketbilling.callback.GetSkuDetailsCallback
import ir.marketbilling.callback.PurchaseCallback
import ir.marketbilling.callback.PurchaseQueryCallback
import ir.marketbilling.config.PaymentConfiguration
import ir.marketbilling.config.SecurityCheck
import ir.marketbilling.constant.MarketIntent
import ir.marketbilling.constant.MarketIntent.REQUEST_SKU_DETAILS_LIST
import ir.marketbilling.constant.Billing
import ir.marketbilling.constant.MarketConfig
import ir.marketbilling.exception.MarketNotFoundException
import ir.marketbilling.exception.MarketNotSupportedException
import ir.marketbilling.exception.ConsumeFailedException
import ir.marketbilling.exception.DisconnectException
import ir.marketbilling.exception.IAPNotSupportedException
import ir.marketbilling.exception.PurchaseHijackedException
import ir.marketbilling.exception.ResultNotOkayException
import ir.marketbilling.exception.SubsNotSupportedException
import ir.marketbilling.getPackageInfo
import ir.marketbilling.receiver.BillingReceiver
import ir.marketbilling.receiver.BillingReceiverCommunicator
import ir.marketbilling.request.PurchaseRequest
import ir.marketbilling.request.purchaseExtraData
import ir.marketbilling.sdkAwareVersionCode
import ir.marketbilling.security.Security
import ir.marketbilling.takeIf
import java.lang.ref.WeakReference

internal class ReceiverBillingConnection(
    private val paymentConfiguration: PaymentConfiguration,
    private val queryFunction: QueryFunction
) : BillingConnectionCommunicator {

    private var consumeCallback: (ConsumeCallback.() -> Unit)? = null
    private var queryCallback: (PurchaseQueryCallback.() -> Unit)? = null
    private var skuDetailCallback: (GetSkuDetailsCallback.() -> Unit)? = null
    private var checkTrialSubscriptionCallback: (CheckTrialSubscriptionCallback.() -> Unit)? = null
    private var featureConfigCallback: (GetFeatureConfigCallback.() -> Unit)? = null

    private var connectionCallbackReference: WeakReference<ConnectionCallback>? = null
    private var contextReference: WeakReference<Context>? = null

    private var receiverCommunicator: BillingReceiverCommunicator? = null
    private var disconnected: Boolean = false
    private var marketVersionCode: Long = 0L
    private var marketConfig: MarketConfig? = null

    private var purchaseWeakReference: WeakReference<PurchaseWeakHolder>? = null

    // Every action is derived from the market's own package name, supplied at runtime
    // through MarketConfig. No market identity is ever compared or embedded here.
    private val actionPrefix: String
        get() = "${marketConfig?.packageName.orEmpty()}."

    private val actionBillingSupport: String get() = actionPrefix + "billingSupport"
    private val actionConsume: String get() = actionPrefix + "consume"
    private val actionPurchase: String get() = actionPrefix + "purchase"
    private val actionQueryPurchases: String get() = actionPrefix + "getPurchase"
    private val actionGetSkuDetail: String get() = actionPrefix + "skuDetail"
    private val actionGetFeatureConfig: String get() = actionPrefix + "featureConfig"
    private val actionCheckTrialSubscription: String get() = actionPrefix + "checkTrialSubscription"

    private val actionReceiveBillingSupport: String
        get() = actionBillingSupport + ACTION_RECEIVE_SUFFIX
    private val actionReceiveConsume: String get() = actionConsume + ACTION_RECEIVE_SUFFIX
    private val actionReceivePurchase: String get() = actionPurchase + ACTION_RECEIVE_SUFFIX
    private val actionReceiveQueryPurchases: String
        get() = actionQueryPurchases + ACTION_RECEIVE_SUFFIX
    private val actionReceiveSkuDetails: String
        get() = actionGetSkuDetail + ACTION_RECEIVE_SUFFIX
    private val actionReceiveGetFeatureConfig: String
        get() = actionGetFeatureConfig + ACTION_RECEIVE_SUFFIX
    private val actionReceiveCheckTrialSubscription: String
        get() = actionCheckTrialSubscription + ACTION_RECEIVE_SUFFIX

    override fun startConnection(
        context: Context,
        callback: ConnectionCallback
    ): ConnectionResult {
        connectionCallbackReference = WeakReference(callback)
        contextReference = WeakReference(context)

        val marketConfig = MarketConfig.from(context)
        this.marketConfig = marketConfig

        if (Security.verifyMarketIsInstalled(context, marketConfig).not()) {
            return ConnectionResult.Failed(MarketNotFoundException())
        }

        marketVersionCode = getPackageInfo(context, marketConfig.packageName)?.let {
            sdkAwareVersionCode(it)
        } ?: 0L

        return when {
            canConnectWithReceiverComponent() -> {
                createReceiverConnection()
                registerBroadcast()
                isPurchaseTypeSupported()
                ConnectionResult.Success
            }
            else -> {
                ConnectionResult.Failed(MarketNotSupportedException())
            }
        }
    }

    private fun canConnectWithReceiverComponent(): Boolean {
        val minVersion = marketConfig?.receiverConnectionMinVersion ?: Long.MAX_VALUE
        return marketVersionCode > minVersion
    }

    private fun createReceiverConnection() {
        receiverCommunicator = object : BillingReceiverCommunicator {
            override fun onNewBroadcastReceived(intent: Intent?) {
                intent?.action?.takeIf(
                    thisIsTrue = {
                        isBundleSignatureValid(intent.extras)
                    },
                    andIfNot = {
                        connectionCallbackReference?.get()?.connectionFailed?.invoke(
                            PurchaseHijackedException()
                        )
                    }
                )?.takeIf(
                    thisIsTrue = {
                        !disconnected
                    },
                    andIfNot = {
                        connectionCallbackReference?.get()?.connectionFailed?.invoke(
                            DisconnectException()
                        )
                    }
                )?.let { action ->
                    onActionReceived(action, intent.extras)
                }
            }
        }
    }

    private fun onActionReceived(action: String, extras: Bundle?) {
        when (action) {
            actionReceiveBillingSupport -> {
                onBillingSupportActionReceived(extras)
            }
            actionReceiveConsume -> {
                onConsumeActionReceived(extras)
            }
            actionReceivePurchase -> {
                onPurchaseReceived(extras)
            }
            actionReceiveQueryPurchases -> {
                onQueryPurchaseReceived(extras)
            }
            actionReceiveSkuDetails -> {
                onGetSkuDetailsReceived(extras)
            }
            actionReceiveGetFeatureConfig -> {
                onGetFeatureConfigReceived(extras)
            }
            actionReceiveCheckTrialSubscription -> {
                onCheckTrialSubscriptionReceived(extras)
            }
        }
    }

    private fun isPurchaseTypeSupported() {
        getNewIntentForBroadcast().apply {
            action = actionBillingSupport
        }.run(::sendBroadcast)
    }

    override fun consume(purchaseToken: String, callback: ConsumeCallback.() -> Unit) {
        consumeCallback = callback

        getNewIntentForBroadcast().apply {
            action = actionConsume
            putExtra(KEY_TOKEN, purchaseToken)
        }.run(::sendBroadcast)
    }

    override fun queryPurchasedProducts(
        purchaseType: PurchaseType,
        callback: PurchaseQueryCallback.() -> Unit
    ) {
        queryCallback = callback
        getNewIntentForBroadcast().apply {
            action = actionQueryPurchases
            putExtra(KEY_ITEM_TYPE, purchaseType.type)
        }.run(::sendBroadcast)
    }

    override fun purchase(
        paymentLauncher: PaymentLauncher,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit
    ) {
        purchaseWeakReference = WeakReference(
            PurchaseWeakHolder(paymentLauncher, callback)
        )

        sendPurchaseBroadcast(purchaseRequest, purchaseType, callback)
    }

    override fun getSkuDetails(
        request: SkuDetailFunctionRequest,
        callback: GetSkuDetailsCallback.() -> Unit
    ) {
        skuDetailCallback = callback
        getNewIntentForBroadcast().apply {
            action = actionGetSkuDetail
            putExtra(KEY_ITEM_TYPE, request.purchaseType.type)
            putStringArrayListExtra(REQUEST_SKU_DETAILS_LIST, ArrayList(request.skuIds))
        }.run(::sendBroadcast)
    }

    private fun getFeatureConfig(
        callback: GetFeatureConfigCallback.() -> Unit
    ) {
        featureConfigCallback = callback
        getNewIntentForBroadcast().apply {
            action = actionGetFeatureConfig
        }.run(::sendBroadcast)
    }

    override fun checkTrialSubscription(
        request: CheckTrialSubscriptionFunctionRequest,
        callback: CheckTrialSubscriptionCallback.() -> Unit
    ) {
        checkTrialSubscriptionCallback = callback

        // A market that declares no trial-subscription capability also declares no
        // receiver for this broadcast. Waiting for a reply that will never arrive
        // would hang the caller forever, so fail fast through the existing callback
        // path instead of ever sending the broadcast.
        if (marketConfig?.supportsTrialSubscription != true) {
            CheckTrialSubscriptionCallback()
                .apply(requireNotNull(checkTrialSubscriptionCallback))
                .checkTrialSubscriptionFailed
                .invoke(MarketNotSupportedException())
            return
        }

        isFeatureSupportedByMarket(
            feature = Feature.CHECK_TRIAL_SUBSCRIPTION,
            isSupported = {
                getNewIntentForBroadcast().apply {
                    action = actionCheckTrialSubscription
                }.run(::sendBroadcast)
            },
            error = {
                CheckTrialSubscriptionCallback()
                    .apply(requireNotNull(checkTrialSubscriptionCallback))
                    .checkTrialSubscriptionFailed
                    .invoke(it)
            }
        )
    }

    private fun isFeatureSupportedByMarket(
        feature: Feature,
        isSupported: () -> Unit,
        error: (Exception) -> Unit
    ) {
        if (isMarketVersionSupportedFeatureConfig().not()) {
            error.invoke(MarketNotSupportedException())
            return
        }

        getFeatureConfig {
            getFeatureConfigSucceed { bundle ->
                if (isFeatureAvailable(featureConfigBundle = bundle, feature)) {
                    isSupported.invoke()
                } else {
                    error.invoke(MarketNotSupportedException())
                }
            }
            getFeatureConfigFailed {
                error.invoke(it)
            }
        }
    }

    private fun isMarketVersionSupportedFeatureConfig(): Boolean {
        val minVersion = marketConfig?.featureConfigMinVersion ?: Long.MAX_VALUE
        return marketVersionCode >= minVersion
    }

    private fun sendPurchaseBroadcast(
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit
    ) {
        PurchaseCallback().apply(callback).purchaseFlowBegan.invoke()
        getNewIntentForBroadcast().apply {
            action = actionPurchase
            putExtra(KEY_SKU, purchaseRequest.productId)
            putExtra(KEY_DEVELOPER_PAYLOAD, purchaseRequest.payload)
            putExtra(KEY_ITEM_TYPE, purchaseType.type)
            putExtra(KEY_EXTRA_INFO, purchaseRequest.purchaseExtraData())
        }.run(::sendBroadcast)
    }

    override fun stopConnection() {
        disconnected = true

        clearReferences()

        receiverCommunicator?.let(BillingReceiver::removeObserver)
        receiverCommunicator = null
    }

    private fun clearReferences() {
        consumeCallback = null
        queryCallback = null
        skuDetailCallback = null
        checkTrialSubscriptionCallback = null
        featureConfigCallback = null
        connectionCallbackReference = null
        contextReference = null

        purchaseWeakReference?.clear()
        purchaseWeakReference = null
    }

    private fun onQueryPurchaseReceived(extras: Bundle?) {
        queryCallback?.let {
            queryFunction.function(
                QueryFunctionRequest(
                    purchaseType = "",
                    queryBundle = { _, _ -> extras },
                    callback = it
                )
            )
        }
    }

    private fun onPurchaseReceived(extras: Bundle?) {
        if (isResponseSucceed(extras)) {
            when {
                purchaseWeakReference?.get() != null -> {
                    startPurchaseActivity(
                        requireNotNull(purchaseWeakReference?.get()),
                        getPurchaseIntent(extras)
                    )
                }
                else -> {
                    // invalid state, we receive purchase but all reference is null, might be connection disconnected
                }
            }
        } else {
            getPurchaseCallback()?.let { purchaseCallback ->
                PurchaseCallback()
                    .apply(purchaseCallback)
                    .failedToBeginFlow.invoke(DisconnectException())
            }
        }
    }

    private fun getPurchaseCallback(): (PurchaseCallback.() -> Unit)? {
        return when {
            purchaseWeakReference?.get() != null -> {
                purchaseWeakReference?.get()?.callback
            }
            else -> {
                null
            }
        }
    }

    private fun startPurchaseActivity(
        purchaseWeakHolder: PurchaseWeakHolder,
        purchaseIntent: Intent?
    ) {
        purchaseWeakHolder.paymentLauncher.activityLauncher.launch(purchaseIntent)
    }

    private fun getPurchaseIntent(extras: Bundle?): Intent? {
        return extras?.getParcelable(KEY_RESPONSE_BUY_INTENT)
    }

    private fun onConsumeActionReceived(extras: Bundle?) {
        if (consumeCallback == null) {
            return
        }

        ConsumeCallback().apply(requireNotNull(consumeCallback)).run {
            if (isResponseSucceed(extras)) {
                consumeSucceed.invoke()
            } else {
                consumeFailed.invoke(ConsumeFailedException())
            }
        }
    }

    private fun onGetSkuDetailsReceived(extras: Bundle?) {
        if (skuDetailCallback == null) {
            return
        }

        if (isResponseSucceed(extras)) {
            val response = extractSkuDetailDataFromBundle(requireNotNull(extras))
            GetSkuDetailsCallback()
                .apply(requireNotNull(skuDetailCallback))
                .getSkuDetailsSucceed.invoke(requireNotNull(response))
        } else {
            GetSkuDetailsCallback().apply(requireNotNull(skuDetailCallback)).run {
                getSkuDetailsFailed.invoke(ResultNotOkayException())
            }
        }
    }

    private fun onGetFeatureConfigReceived(extras: Bundle?) {
        if (featureConfigCallback == null) {
            return
        }

        if (isResponseSucceed(extras)) {
            GetFeatureConfigCallback()
                .apply(requireNotNull(featureConfigCallback))
                .getFeatureConfigSucceed.invoke(requireNotNull(extras))
        } else {
            GetFeatureConfigCallback()
                .apply(requireNotNull(featureConfigCallback)).run {
                    getFeatureConfigFailed.invoke(ResultNotOkayException())
                }
        }
    }

    private fun onCheckTrialSubscriptionReceived(extras: Bundle?) {
        if (checkTrialSubscriptionCallback == null) {
            return
        }

        if (isResponseSucceed(extras)) {
            val response = extractTrialSubscriptionDataFromBundle(requireNotNull(extras))
            CheckTrialSubscriptionCallback()
                .apply(requireNotNull(checkTrialSubscriptionCallback))
                .checkTrialSubscriptionSucceed.invoke(requireNotNull(response))
        } else {
            CheckTrialSubscriptionCallback()
                .apply(requireNotNull(checkTrialSubscriptionCallback)).run {
                    checkTrialSubscriptionFailed.invoke(ResultNotOkayException())
                }
        }
    }

    private fun onBillingSupportActionReceived(extras: Bundle?) {
        val isResponseSucceed = isResponseSucceed(extras)
        val isSubscriptionSupport = isSubscriptionSupport(extras)

        when {
            isResponseSucceed && isSubscriptionSupport -> {
                connectionCallbackReference?.get()?.connectionSucceed?.invoke()
            }
            !isResponseSucceed -> {
                connectionCallbackReference?.get()?.connectionFailed?.invoke(
                    IAPNotSupportedException()
                )
            }
            else -> {
                connectionCallbackReference?.get()?.connectionFailed?.invoke(
                    SubsNotSupportedException()
                )
            }
        }
    }

    private fun isSubscriptionSupport(extras: Bundle?): Boolean {
        // A market that declares no subscription capability at all overrides whatever
        // the broadcast reply claims - it never gets the chance to claim support. Fails
        // closed like every other default in this class: an unset marketConfig (not
        // reachable in practice - it is always assigned before this can be called)
        // means "not supported" rather than "supported".
        val marketSupportsSubscription = marketConfig?.supportsSubscription ?: false
        val isSubscriptionSupport = extras?.getBoolean(KEY_SUBSCRIPTION_SUPPORT) ?: false
        return !paymentConfiguration.shouldSupportSubscription ||
            (marketSupportsSubscription && isSubscriptionSupport)
    }

    private fun isResponseSucceed(extras: Bundle?): Boolean {
        return extras?.getInt(RESPONSE_CODE) == MarketIntent.RESPONSE_RESULT_OK
    }

    private fun isBundleSignatureValid(extras: Bundle?): Boolean {
        return getSecureSignature() == extras?.getString(KEY_SECURE)
    }

    private fun registerBroadcast() {
        BillingReceiver.addObserver(requireNotNull(receiverCommunicator))
    }

    private fun getNewIntentForBroadcast(): Intent {
        val bundle = Bundle().apply {
            putString(KEY_PACKAGE_NAME, contextReference?.get()?.packageName)
            putString(KEY_SECURE, getSecureSignature())
            putInt(KEY_API_VERSION, Billing.IN_APP_BILLING_VERSION)
        }
        return Intent().apply {
            `package` = marketConfig?.packageName
            marketConfig?.receiverComponentName?.let { receiverClassName ->
                component = ComponentName(marketConfig?.packageName.orEmpty(), receiverClassName)
            }
            putExtras(bundle)
        }
    }

    private fun getSecureSignature(): String {
        val rsaKey = (paymentConfiguration.localSecurityCheck as? SecurityCheck.Enable)
            ?.rsaPublicKey

        return rsaKey ?: DEFAULT_SECURE_SIGNATURE
    }

    private fun sendBroadcast(intent: Intent) {
        contextReference?.get()?.sendBroadcast(intent)
    }

    companion object {

        private const val DEFAULT_SECURE_SIGNATURE = "secureBroadcastKey"

        // Market-independent: this is the suffix BillingReceiver.onReceive appends to
        // whatever action the market's own broadcast carried.
        private const val ACTION_RECEIVE_SUFFIX = ".iab"

        private const val KEY_SUBSCRIPTION_SUPPORT = "subscriptionSupport"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_API_VERSION = "apiVersion"
        private const val KEY_SKU = "sku"
        private const val KEY_ITEM_TYPE = "itemType"
        private const val KEY_DEVELOPER_PAYLOAD = "developerPayload"
        private const val KEY_EXTRA_INFO = "extraInfo"
        private const val KEY_SECURE = "secure"
        private const val KEY_RESPONSE_BUY_INTENT = "BUY_INTENT"
        private const val RESPONSE_CODE = "RESPONSE_CODE"
        private const val KEY_TOKEN = "token"
    }
}
