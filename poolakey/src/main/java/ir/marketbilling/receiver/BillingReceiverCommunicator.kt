package ir.marketbilling.receiver

import android.content.Intent

internal interface BillingReceiverCommunicator {
    fun onNewBroadcastReceived(intent: Intent?)
}
