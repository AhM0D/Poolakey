package ir.marketbilling.exception

class MarketNotFoundException : IllegalStateException() {

    override val message: String?
        get() = "Market is not installed"

}
