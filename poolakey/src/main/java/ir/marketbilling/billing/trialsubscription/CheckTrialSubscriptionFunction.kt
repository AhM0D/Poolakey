package ir.marketbilling.billing.trialsubscription

import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import com.android.vending.billing.IInAppBillingService
import ir.marketbilling.billing.BillingFunction
import ir.marketbilling.billing.Feature
import ir.marketbilling.billing.FeatureConfig
import ir.marketbilling.callback.CheckTrialSubscriptionCallback
import ir.marketbilling.constant.MarketIntent
import ir.marketbilling.entity.TrialSubscriptionInfo
import ir.marketbilling.exception.MarketNotSupportedException
import ir.marketbilling.exception.ResultNotOkayException
import ir.marketbilling.takeIf
import ir.marketbilling.thread.PoolakeyThread

internal class CheckTrialSubscriptionFunction(
    private val context: Context,
    private val mainThread: PoolakeyThread<() -> Unit>
) : BillingFunction<CheckTrialSubscriptionFunctionRequest> {

    override fun function(
        billingService: IInAppBillingService,
        request: CheckTrialSubscriptionFunctionRequest
    ): Unit = with(request) {
        try {
            val isCheckTrialSubscriptionAvailable = FeatureConfig.isFeatureAvailable(
                featureConfigBundle = billingService.featureConfig,
                feature = Feature.CHECK_TRIAL_SUBSCRIPTION
            )
            if (isCheckTrialSubscriptionAvailable.not()) {
                CheckTrialSubscriptionCallback().apply(callback)
                    .checkTrialSubscriptionFailed
                    .invoke(MarketNotSupportedException())
                return@with
            }

            billingService.checkTrialSubscription(
                context.packageName,
            )?.takeIfIsResponseOKOrThrowException(
                mainThread,
                callback
            )?.takeIfBundleContainsCorrectResponseKeyOrThrowException(
                mainThread,
                callback
            )?.let { bundle ->
                extractTrialSubscriptionDataFromBundle(bundle)
            }?.also { items ->
                mainThread.execute {
                    CheckTrialSubscriptionCallback().apply(callback)
                        .checkTrialSubscriptionSucceed
                        .invoke(items)
                }
            }
        } catch (e: RemoteException) {
            mainThread.execute {
                CheckTrialSubscriptionCallback().apply(callback)
                    .checkTrialSubscriptionFailed.invoke(e)
            }
        }
    }
}

internal fun extractTrialSubscriptionDataFromBundle(
    bundle: Bundle
): TrialSubscriptionInfo? {
    return bundle.getString(MarketIntent.RESPONSE_CHECK_TRIAL_SUBSCRIPTION_DATA)?.let {
        TrialSubscriptionInfo.fromJson(it)
    }
}

private fun Bundle.takeIfBundleContainsCorrectResponseKeyOrThrowException(
    mainThread: PoolakeyThread<() -> Unit>,
    callback: CheckTrialSubscriptionCallback.() -> Unit
): Bundle? {
    return takeIf(
        thisIsTrue = { bundle ->
            bundle.containsKey(MarketIntent.RESPONSE_CHECK_TRIAL_SUBSCRIPTION_DATA)
        },
        andIfNot = {
            mainThread.execute {
                CheckTrialSubscriptionCallback().apply(callback)
                    .checkTrialSubscriptionFailed
                    .invoke(IllegalStateException("Missing data from the received result"))
            }
        }
    )
}

private fun Bundle.takeIfIsResponseOKOrThrowException(
    mainThread: PoolakeyThread<() -> Unit>,
    callback: CheckTrialSubscriptionCallback.() -> Unit
): Bundle? {
    return takeIf(
        thisIsTrue = { bundle ->
            bundle.get(MarketIntent.RESPONSE_CODE) == MarketIntent.RESPONSE_RESULT_OK
        },
        andIfNot = {
            mainThread.execute {
                CheckTrialSubscriptionCallback().apply(callback)
                    .checkTrialSubscriptionFailed
                    .invoke(ResultNotOkayException())
            }
        }
    )
}
