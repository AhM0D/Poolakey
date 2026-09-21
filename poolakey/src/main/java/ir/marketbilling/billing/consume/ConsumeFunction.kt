package ir.marketbilling.billing.consume

import android.content.Context
import android.os.RemoteException
import com.android.vending.billing.IInAppBillingService
import ir.marketbilling.billing.BillingFunction
import ir.marketbilling.callback.ConsumeCallback
import ir.marketbilling.constant.MarketIntent
import ir.marketbilling.constant.Billing
import ir.marketbilling.exception.ConsumeFailedException
import ir.marketbilling.takeIf
import ir.marketbilling.thread.PoolakeyThread

internal class ConsumeFunction(
    private val mainThread: PoolakeyThread<() -> Unit>,
    private val context: Context
) : BillingFunction<ConsumeFunctionRequest> {

    override fun function(
        billingService: IInAppBillingService,
        request: ConsumeFunctionRequest
    ): Unit = with(request) {
        try {
            billingService.consumePurchase(Billing.IN_APP_BILLING_VERSION, context.packageName, purchaseToken)
                .takeIf(
                    thisIsTrue = { it == MarketIntent.RESPONSE_RESULT_OK },
                    andIfNot = {
                        mainThread.execute {
                            ConsumeCallback().apply(callback)
                                .consumeFailed
                                .invoke(ConsumeFailedException())
                        }
                    }
                )
                ?.also {
                    mainThread.execute {
                        ConsumeCallback().apply(callback).consumeSucceed.invoke()
                    }
                }
        } catch (e: RemoteException) {
            mainThread.execute {
                ConsumeCallback().apply(callback).consumeFailed.invoke(e)
            }
        }
    }

}