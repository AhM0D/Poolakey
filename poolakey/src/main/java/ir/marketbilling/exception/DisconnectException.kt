package ir.marketbilling.exception

class DisconnectException : IllegalStateException() {

    override val message: String?
        get() = "We can't communicate with the market: Service is disconnected"

}
