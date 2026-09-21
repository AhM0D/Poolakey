package ir.marketbilling.exception

class DynamicPriceNotSupportedException : IllegalStateException() {

    override val message: String
        get() = "Dynamic price not supported"

}
