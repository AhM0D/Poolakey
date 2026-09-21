package ir.marketbilling.billing.purchase

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import com.android.vending.billing.IInAppBillingService
import ir.marketbilling.PurchaseType
import ir.marketbilling.billing.BillingFunction
import ir.marketbilling.callback.PurchaseCallback
import ir.marketbilling.constant.MarketIntent
import ir.marketbilling.constant.Billing
import ir.marketbilling.exception.DynamicPriceNotSupportedException
import ir.marketbilling.exception.ResultNotOkayException
import ir.marketbilling.request.PurchaseRequest
import ir.marketbilling.request.purchaseExtraData
import ir.marketbilling.takeIf

internal class PurchaseFunction(
    private val context: Context
) : BillingFunction<PurchaseFunctionRequest> {

    override fun function(
        billingService: IInAppBillingService,
        request: PurchaseFunctionRequest
    ): Unit = with(request) {
        try {
            val purchaseConfigBundle = billingService.getPurchaseConfig(
                Billing.IN_APP_BILLING_VERSION
            )

            val intentResponseIsNullError = {
                PurchaseCallback().apply(callback)
                    .failedToBeginFlow
                    .invoke(IllegalStateException("Couldn't receive buy intent from the market"))
            }

            when {
                isMarketSupportIntentV3(purchaseConfigBundle) -> {
                    launchBuyIntentV3(billingService, intentResponseIsNullError)
                }
                isMarketSupportIntentV2(purchaseConfigBundle) -> {
                    launchBuyIntentV2(billingService, intentResponseIsNullError)
                }
                else -> {
                    launchBuyIntent(billingService, intentResponseIsNullError)
                }
            }
        } catch (e: RemoteException) {
            PurchaseCallback().apply(callback).failedToBeginFlow.invoke(e)
        }
    }

    private fun PurchaseFunctionRequest.launchBuyIntentV3(
        billingService: IInAppBillingService,
        intentResponseIsNullError: () -> Unit
    ) {
        getBuyIntentV3FromBillingService(
            billingService,
            purchaseRequest,
            purchaseRequest.purchaseExtraData(),
            callback
        )?.takeIf(
            thisIsTrue = { bundle ->
                bundle.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY) != null
            }, andIfNot = intentResponseIsNullError
        )?.getParcelable<Intent>(INTENT_RESPONSE_BUY)?.also { purchaseIntent ->
            launchIntent.invoke(purchaseIntent)
        }
    }

    private fun PurchaseFunctionRequest.launchBuyIntentV2(
        billingService: IInAppBillingService,
        intentResponseIsNullError: () -> Unit
    ) {

        if (purchaseRequest.dynamicPriceToken.isNullOrEmpty().not()) {
            PurchaseCallback().apply(callback).failedToBeginFlow.invoke(
                DynamicPriceNotSupportedException()
            )
            return
        }

        getBuyIntentV2FromBillingService(
            billingService,
            purchaseRequest,
            purchaseType,
            callback
        )?.takeIf(
            thisIsTrue = { bundle ->
                bundle.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY) != null
            }, andIfNot = intentResponseIsNullError
        )?.getParcelable<Intent>(INTENT_RESPONSE_BUY)?.also { purchaseIntent ->
            launchIntent.invoke(purchaseIntent)
        }
    }

    private fun PurchaseFunctionRequest.launchBuyIntent(
        billingService: IInAppBillingService,
        intentResponseIsNullError: () -> Unit
    ) {
        if (purchaseRequest.dynamicPriceToken.isNullOrEmpty().not()) {
            PurchaseCallback().apply(callback).failedToBeginFlow.invoke(
                DynamicPriceNotSupportedException()
            )
            return
        }

        getBuyIntentFromBillingService(
            billingService,
            purchaseRequest,
            purchaseType,
            callback
        )?.takeIf(
            thisIsTrue = { bundle ->
                bundle.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY) != null
            }, andIfNot = intentResponseIsNullError
        )?.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY)?.also { purchaseIntent ->
            launchIntentWithIntentSender.invoke(purchaseIntent.intentSender)
        }
    }

    private inline fun getBuyIntentFromBillingService(
        billingService: IInAppBillingService,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit
    ) = billingService.getBuyIntent(
        Billing.IN_APP_BILLING_VERSION,
        context.packageName,
        purchaseRequest.productId,
        purchaseType.type,
        purchaseRequest.payload
    )?.takeBundleIfResponseIsOk(callback)

    private inline fun getBuyIntentV2FromBillingService(
        billingService: IInAppBillingService,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseCallback.() -> Unit
    ) = billingService.getBuyIntentV2(
        Billing.IN_APP_BILLING_VERSION,
        context.packageName,
        purchaseRequest.productId,
        purchaseType.type,
        purchaseRequest.payload
    )?.takeBundleIfResponseIsOk(callback)

    private inline fun getBuyIntentV3FromBillingService(
        billingService: IInAppBillingService,
        purchaseRequest: PurchaseRequest,
        extraData: Bundle,
        callback: PurchaseCallback.() -> Unit
    ) = billingService.getBuyIntentV3(
        Billing.IN_APP_BILLING_VERSION,
        context.packageName,
        purchaseRequest.productId,
        purchaseRequest.payload,
        extraData
    )?.takeBundleIfResponseIsOk(callback)

    private inline fun Bundle.takeBundleIfResponseIsOk(
        callback: PurchaseCallback.() -> Unit
    ): Bundle? = takeIf(
        thisIsTrue = { bundle ->
            bundle.get(MarketIntent.RESPONSE_CODE) == MarketIntent.RESPONSE_RESULT_OK
        }, andIfNot = {
            PurchaseCallback().apply(callback)
                .failedToBeginFlow
                .invoke(ResultNotOkayException())
        }
    )

    private fun isMarketSupportIntentV2(purchaseConfigBundle: Bundle?): Boolean {
        return purchaseConfigBundle?.getBoolean(INTENT_V2_SUPPORT) ?: false
    }

    private fun isMarketSupportIntentV3(purchaseConfigBundle: Bundle?): Boolean {
        return purchaseConfigBundle?.getBoolean(INTENT_V3_SUPPORT) ?: false
    }

    companion object {
        private const val INTENT_RESPONSE_BUY = "BUY_INTENT"
        private const val INTENT_V2_SUPPORT = "INTENT_V2_SUPPORT"
        private const val INTENT_V3_SUPPORT = "INTENT_V3_SUPPORT"
    }

}