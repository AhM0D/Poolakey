package ir.marketbilling.billing

import com.android.vending.billing.IInAppBillingService

internal interface BillingFunction<Request : FunctionRequest> {

    fun function(billingService: IInAppBillingService, request: Request)

}