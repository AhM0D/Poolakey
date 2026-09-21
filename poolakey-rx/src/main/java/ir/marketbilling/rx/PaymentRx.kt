package ir.marketbilling.rx

import androidx.activity.result.ActivityResultRegistry
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import ir.marketbilling.Connection
import ir.marketbilling.Payment
import ir.marketbilling.entity.PurchaseInfo
import ir.marketbilling.entity.SkuDetails
import ir.marketbilling.entity.TrialSubscriptionInfo
import ir.marketbilling.request.PurchaseRequest
import ir.marketbilling.rxbase.exception.PurchaseCanceledException

/**
 * You have to use this function to connect to the In-App Billing service. Note that you have to
 * connect to the market's Billing service before using any other available functions, So make sure
 * you call this function before doing anything else, also make sure that you are connected to
 * the billing service through Connection.
 * @see Connection
 * @return Observable that you can subscribe to it and get notified about service connection changes.
 */
fun Payment.connect(): Observable<Connection> {
    var connection: Connection? = null
    return Observable.create<Connection> { emitter ->
        connection = connect {
            connectionSucceed { emitter.onNext(this) }
            disconnected { emitter.onNext(this) }
            connectionFailed { emitter.onError(it) }
        }
    }.doOnDispose {
        connection?.disconnect()
    }
}

/**
 * You can use this function to navigate user to the market's payment activity to purchase a product.
 * Note that for subscribing a product you have to use the 'subscribeProduct' function.
 * @see subscribeProduct
 * @param registry We use this activityResultRegistry instance to actually start the market's payment activity.
 * @param request This contains some information about the product that we are going to purchase.
 * @return Single that you can subscribe to it and get the PurchaseInfo.
 */
fun Payment.purchaseProduct(
    registry: ActivityResultRegistry,
    request: PurchaseRequest
): Single<PurchaseInfo> {
    return Single.create { emitter ->
        purchaseProduct(registry, request) {
            purchaseSucceed { emitter.onSuccess(it) }
            purchaseCanceled { emitter.onError(PurchaseCanceledException()) }
            purchaseFailed { emitter.onError(it) }
            failedToBeginFlow { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to consume an already purchased product. Note that you can't use
 * this function to consume subscribed products. This function runs off the main thread, so you
 * don't have to handle the threading by your self.
 * @param purchaseToken You have received this token when user purchased that particular product.
 * You can also use 'getPurchasedProducts' function to get all the purchased products by this
 * particular user.
 * @see getPurchasedProducts
 * @return Completable that you can subscribe to it and it gets completed when consumption succeeds.
 */
fun Payment.consumeProduct(purchaseToken: String): Completable {
    return Completable.create { emitter ->
        consumeProduct(purchaseToken) {
            consumeSucceed { emitter.onComplete() }
            consumeFailed { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to navigate user to the market's payment activity to subscribe a product.
 * Note that for purchasing a product you have to use the 'purchaseProduct' function.
 * @see purchaseProduct
 * @param registry We use this activityResultRegistry instance to actually start the market's payment activity.
 * @param request This contains some information about the product that we are going to subscribe.
 * @return Single that you can subscribe to it and get the PurchaseInfo.
 */
fun Payment.subscribeProduct(
    registry: ActivityResultRegistry,
    request: PurchaseRequest
): Single<PurchaseInfo> {
    return Single.create { emitter ->
        subscribeProduct(registry, request) {
            purchaseSucceed { emitter.onSuccess(it) }
            purchaseCanceled { emitter.onError(PurchaseCanceledException()) }
            purchaseFailed { emitter.onError(it) }
            failedToBeginFlow { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to query user's purchased products, Note that if you want to query
 * user's subscribed products, you have to use 'getSubscribedProducts' function, since this function
 * will only query purchased products and not the subscribed products. This function runs off
 * the main thread, so you don't have to handle the threading by your self.
 * @see getSubscribedProducts
 * @return Single that you can subscribe to it and get the list of purchased products.
 */
fun Payment.getPurchasedProducts(): Single<List<PurchaseInfo>> {
    return Single.create { emitter ->
        getPurchasedProducts {
            querySucceed { emitter.onSuccess(it) }
            queryFailed { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to query user's subscribed products, Note that if you want to query
 * user's purchased products, you have to use 'getPurchasedProducts' function, since this function
 * will only query subscribed products and not the purchased products. This function runs off
 * the main thread, so you don't have to handle the threading by your self.
 * @see getPurchasedProducts
 * @return Single that you can subscribe to it and get the list of subscribed products.
 */
fun Payment.getSubscribedProducts(): Single<List<PurchaseInfo>> {
    return Single.create { emitter ->
        getSubscribedProducts {
            querySucceed { emitter.onSuccess(it) }
            queryFailed { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to get detail of inApp sku's,
 * @param skuIds This contain all sku id's that you want to get info about it.
 * @return Single that you can subscribe to it and get the detail of requested sku's.
 */
fun Payment.getInAppSkuDetails(
    skuIds: List<String>
): Single<List<SkuDetails>> {
    return Single.create { emitter ->
        getInAppSkuDetails(skuIds) {
            getSkuDetailsSucceed { emitter.onSuccess(it) }
            getSkuDetailsFailed { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to get detail of subscriptions sku's,
 * @param skuIds This contain all sku id's that you want to get info about it.
 * @return Single that you can subscribe to it and get the detail of requested sku's.
 */
fun Payment.getSubscriptionSkuDetails(
    skuIds: List<String>
): Single<List<SkuDetails>> {
    return Single.create { emitter ->
        getSubscriptionSkuDetails(skuIds) {
            getSkuDetailsSucceed { emitter.onSuccess(it) }
            getSkuDetailsFailed { emitter.onError(it) }
        }
    }
}

/**
 * You can use this function to check trial subscription,
 * @return Single that you can subscribe to it and get the trial subscription info.
 */
fun Payment.checkTrialSubscription(): Single<TrialSubscriptionInfo> {
    return Single.create { emitter ->
        checkTrialSubscription {
            checkTrialSubscriptionSucceed { emitter.onSuccess(it) }
            checkTrialSubscriptionFailed { emitter.onError(it) }
        }
    }
}