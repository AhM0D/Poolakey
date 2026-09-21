package ir.marketbilling.billing.purchase

import ir.marketbilling.PaymentLauncher
import ir.marketbilling.callback.PurchaseCallback

internal data class PurchaseWeakHolder(
    val paymentLauncher: PaymentLauncher,
    val callback: PurchaseCallback.() -> Unit
)