package ir.marketbilling.exception

class ResultNotOkayException : IllegalStateException() {

    override val message: String?
        get() = "Failed to receive response from the market"

}
