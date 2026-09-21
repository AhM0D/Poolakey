package ir.marketbilling.config

/**
 * You can use this class to disable or enable local security checks for purchases and queries.
 * Note that it's highly recommended to disable local security checks and use your market's
 * REST API to validate a purchase instead. Check your market's developer documentation for
 * details.
 * @see Disable
 * @see Enable
 */
sealed class SecurityCheck {

    /**
     * You have to use this object in order to disable local security checks.
     */
    object Disable : SecurityCheck()

    /**
     * You have to use this class in order to enable local security checks. You can access your
     * app's public rsa key from your market's developer panel, under its In-App Billing section.
     */
    data class Enable(val rsaPublicKey: String) : SecurityCheck()

}
