package ir.marketbilling.exception

class MarketNotSupportedException : IllegalStateException() {

    override val message: String?
        get() = "Market is not updated"

}
