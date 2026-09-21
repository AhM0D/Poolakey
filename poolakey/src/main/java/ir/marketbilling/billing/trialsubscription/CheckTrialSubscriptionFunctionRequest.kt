package ir.marketbilling.billing.trialsubscription

import ir.marketbilling.billing.FunctionRequest
import ir.marketbilling.callback.CheckTrialSubscriptionCallback

internal class CheckTrialSubscriptionFunctionRequest(
    val callback: CheckTrialSubscriptionCallback.() -> Unit
) : FunctionRequest