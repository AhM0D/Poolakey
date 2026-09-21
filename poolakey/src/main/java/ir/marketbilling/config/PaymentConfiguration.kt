package ir.marketbilling.config

data class PaymentConfiguration @JvmOverloads constructor(
    val localSecurityCheck: SecurityCheck,
    val shouldSupportSubscription: Boolean = true
)