package ir.marketbilling.billing.skudetail

import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import com.android.vending.billing.IInAppBillingService
import ir.marketbilling.billing.BillingFunction
import ir.marketbilling.callback.GetSkuDetailsCallback
import ir.marketbilling.constant.MarketIntent
import ir.marketbilling.constant.Billing
import ir.marketbilling.entity.SkuDetails
import ir.marketbilling.exception.ResultNotOkayException
import ir.marketbilling.takeIf
import ir.marketbilling.thread.PoolakeyThread

internal class GetSkuDetailFunction(
    private val context: Context,
    private val mainThread: PoolakeyThread<() -> Unit>
) : BillingFunction<SkuDetailFunctionRequest> {

    override fun function(
        billingService: IInAppBillingService,
        request: SkuDetailFunctionRequest
    ): Unit = with(request) {
        try {
            val skuBundle = Bundle().apply {
                putStringArrayList(
                    MarketIntent.REQUEST_SKU_DETAILS_LIST,
                    ArrayList(request.skuIds)
                )
            }
            billingService.getSkuDetails(
                Billing.IN_APP_BILLING_VERSION,
                context.packageName,
                request.purchaseType.type,
                skuBundle
            )?.takeIfIsResponseOKOrThrowException(
                mainThread,
                callback
            )?.takeIfBundleContainsCorrectResponseKeyOrThrowException(
                mainThread,
                callback
            )?.let { bundle ->
                extractSkuDetailDataFromBundle(bundle)
            }?.also { items ->
                mainThread.execute {
                    GetSkuDetailsCallback().apply(callback).getSkuDetailsSucceed.invoke(items)
                }
            }
        } catch (e: RemoteException) {
            mainThread.execute {
                GetSkuDetailsCallback().apply(callback).getSkuDetailsFailed.invoke(e)
            }
        }
    }
}

internal fun extractSkuDetailDataFromBundle(
    bundle: Bundle
): List<SkuDetails>? {
    return bundle.getStringArrayList(MarketIntent.RESPONSE_GET_SKU_DETAILS_LIST)?.map {
        SkuDetails.fromJson(it)
    }
}

private fun Bundle.takeIfBundleContainsCorrectResponseKeyOrThrowException(
    mainThread: PoolakeyThread<() -> Unit>,
    callback: GetSkuDetailsCallback.() -> Unit
): Bundle? {
    return takeIf(
        thisIsTrue = { bundle ->
            bundle.containsKey(MarketIntent.RESPONSE_GET_SKU_DETAILS_LIST)
        },
        andIfNot = {
            mainThread.execute {
                GetSkuDetailsCallback().apply(callback)
                    .getSkuDetailsFailed
                    .invoke(IllegalStateException("Missing data from the received result"))
            }
        }
    )
}

private fun Bundle.takeIfIsResponseOKOrThrowException(
    mainThread: PoolakeyThread<() -> Unit>,
    callback: GetSkuDetailsCallback.() -> Unit
): Bundle? {
    return takeIf(
        thisIsTrue = { bundle ->
            bundle.get(MarketIntent.RESPONSE_CODE) == MarketIntent.RESPONSE_RESULT_OK
        },
        andIfNot = {
            mainThread.execute {
                GetSkuDetailsCallback().apply(callback)
                    .getSkuDetailsFailed
                    .invoke(ResultNotOkayException())
            }
        }
    )
}
