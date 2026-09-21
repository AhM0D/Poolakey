package ir.marketbilling

import android.content.Context
import androidx.activity.result.ActivityResultRegistry
import ir.marketbilling.billing.query.QueryFunction
import ir.marketbilling.billing.skudetail.GetSkuDetailFunction
import ir.marketbilling.billing.trialsubscription.CheckTrialSubscriptionFunction
import ir.marketbilling.callback.CheckTrialSubscriptionCallback
import ir.marketbilling.callback.ConnectionCallback
import ir.marketbilling.callback.ConsumeCallback
import ir.marketbilling.callback.GetSkuDetailsCallback
import ir.marketbilling.callback.PurchaseCallback
import ir.marketbilling.callback.PurchaseQueryCallback
import ir.marketbilling.config.PaymentConfiguration
import ir.marketbilling.mapper.RawDataToPurchaseInfo
import ir.marketbilling.request.PurchaseRequest
import ir.marketbilling.security.PurchaseVerifier
import ir.marketbilling.thread.BackgroundThread
import ir.marketbilling.thread.MainThread
import ir.marketbilling.thread.BillingThread

class Payment(
    context: Context,
    config: PaymentConfiguration
) {

    private val backgroundThread: BillingThread<Runnable> = BackgroundThread()
    private val mainThread: BillingThread<() -> Unit> = MainThread()

    private val purchaseVerifier = PurchaseVerifier()
    private val rawDataToPurchaseInfo = RawDataToPurchaseInfo()

    private val queryFunction = QueryFunction(
        rawDataToPurchaseInfo,
        purchaseVerifier,
        config,
        mainThread,
    )

    private val getSkuFunction = GetSkuDetailFunction(
        context,
        mainThread
    )

    private val checkTrialSubscriptionFunction = CheckTrialSubscriptionFunction(
        context,
        mainThread
    )

    private val purchaseResultParser = PurchaseResultParser(rawDataToPurchaseInfo, purchaseVerifier)

    private val connection = BillingConnection(
        context = context,
        paymentConfiguration = config,
        queryFunction = queryFunction,
        backgroundThread = backgroundThread,
        skuDetailFunction = getSkuFunction,
        purchaseResultParser = purchaseResultParser,
        checkTrialSubscriptionFunction = checkTrialSubscriptionFunction,
        mainThread = mainThread
    )

    /**
     * You have to use this function to connect to the In-App Billing service. Note that you have to
     * connect to the market's Billing service before using any other available functions, So make sure
     * you call this function before doing anything else, also make sure that you are connected to
     * the billing service through Connection.
     * @see Connection
     * @param callback You have to use this callback in order to get notified about the service
     * connection changes.
     * @return a Connection interface which you can use to disconnect from the
     * service or get the current connection state.
     */
    fun connect(callback: ConnectionCallback.() -> Unit): Connection {
        return connection.startConnection(callback)
    }

    /**
     * You can use this function to navigate user to the market's payment activity to purchase a product.
     * Note that for subscribing a product you have to use the 'subscribeProduct' function.
     * @see subscribeProduct
     * @param registry We use this activityResultRegistry instance to actually start the market's payment activity.
     * @param request This contains some information about the product that we are going to purchase.
     * @param callback You have to use this callback in order to get notified about the purchase flow.
     */
    fun purchaseProduct(
        registry: ActivityResultRegistry,
        request: PurchaseRequest,
        callback: PurchaseCallback.() -> Unit
    ) {
        connection.purchase(registry, request, PurchaseType.IN_APP, callback)
    }

    /**
     * You can use this function to consume an already purchased product. Note that you can't use
     * this function to consume subscribed products. This function runs off the main thread, so you
     * don't have to handle the threading by your self.
     * @param purchaseToken You have received this token when user purchased that particular product.
     * You can also use 'getPurchasedProducts' function to get all the purchased products by this
     * particular user.
     * @param callback You have to use callback in order to get notified if product consumption was
     * successful or not.
     * @see getPurchasedProducts
     */
    fun consumeProduct(purchaseToken: String, callback: ConsumeCallback.() -> Unit) {
        connection.consume(purchaseToken, callback)
    }

    /**
     * You can use this function to navigate user to the market's payment activity to subscribe a product.
     * Note that for purchasing a product you have to use the 'purchaseProduct' function.
     * @see purchaseProduct
     * @param registry We use this activityResultRegistry instance to actually start the market's payment activity.
     * @param request This contains some information about the product that we are going to subscribe.
     * @param callback You have to use callback in order to get notified about the purchase flow.
     */
    fun subscribeProduct(
        registry: ActivityResultRegistry,
        request: PurchaseRequest,
        callback: PurchaseCallback.() -> Unit
    ) {
        connection.purchase(registry, request, PurchaseType.SUBSCRIPTION, callback)
    }

    /**
     * You can use this function to query user's purchased products, Note that if you want to query
     * user's subscribed products, you have to use 'getSubscribedProducts' function, since this function
     * will only query purchased products and not the subscribed products. This function runs off
     * the main thread, so you don't have to handle the threading by your self.
     * @see getSubscribedProducts
     * @param callback You have to use callback in order to get notified about query's result.
     */
    fun getPurchasedProducts(callback: PurchaseQueryCallback.() -> Unit) {
        connection.queryPurchasedProducts(PurchaseType.IN_APP, callback)
    }

    /**
     * You can use this function to query user's subscribed products, Note that if you want to query
     * user's purchased products, you have to use 'getPurchasedProducts' function, since this function
     * will only query subscribed products and not the purchased products. This function runs off
     * the main thread, so you don't have to handle the threading by your self.
     * @see getPurchasedProducts
     * @param callback You have to use callback in order to get notified about query's result.
     */
    fun getSubscribedProducts(callback: PurchaseQueryCallback.() -> Unit) {
        connection.queryPurchasedProducts(PurchaseType.SUBSCRIPTION, callback)
    }

    /**
     * You can use this function to get detail of inApp product sku's,
     * @param skuIds This contain all sku id's that you want to get info about it.
     * @param callback You have to use callback in order to get detail of requested sku's.
     */
    fun getInAppSkuDetails(
        skuIds: List<String>,
        callback: GetSkuDetailsCallback.() -> Unit
    ) {
        connection.getSkuDetail(PurchaseType.IN_APP, skuIds, callback)
    }

    /**
     * You can use this function to get detail of subscription product sku's,
     * @param skuIds This contain all sku id's that you want to get info about it.
     * @param callback You have to use callback in order to get detail of requested sku's.
     */
    fun getSubscriptionSkuDetails(
        skuIds: List<String>,
        callback: GetSkuDetailsCallback.() -> Unit
    ) {
        connection.getSkuDetail(PurchaseType.SUBSCRIPTION, skuIds, callback)
    }

    /**
     * You can use this function to check trial subscription,
     * @param callback You have to use callback in order to get notified about check trial subscription result.
     */
    fun checkTrialSubscription(
        callback: CheckTrialSubscriptionCallback.() -> Unit
    ) {
        connection.checkTrialSubscription(callback)
    }
}
