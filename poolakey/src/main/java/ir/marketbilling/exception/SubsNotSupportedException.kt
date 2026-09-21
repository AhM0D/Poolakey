package ir.marketbilling.exception

class SubsNotSupportedException : IllegalAccessException() {

    override val message: String?
        get() = "Subscription is not supported in this version of the installed market"

}
