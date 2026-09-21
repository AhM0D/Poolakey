package ir.marketbilling.mapper

import ir.marketbilling.constant.RawJson
import ir.marketbilling.entity.PurchaseInfo
import ir.marketbilling.entity.PurchaseState
import org.json.JSONObject

internal class RawDataToPurchaseInfo {

    fun mapToPurchaseInfo(purchaseData: String, dataSignature: String): PurchaseInfo {
        return JSONObject(purchaseData).run {
            PurchaseInfo(
                orderId = optString(RawJson.ORDER_ID),
                purchaseToken = optString(RawJson.PURCHASE_TOKEN),
                payload = optString(RawJson.DEVELOPER_PAYLOAD),
                packageName = optString(RawJson.PACKAGE_NAME),
                purchaseState = if (optInt(RawJson.PURCHASE_STATE) == 0) {
                    PurchaseState.PURCHASED
                } else {
                    PurchaseState.REFUNDED
                },
                purchaseTime = optLong(RawJson.PURCHASE_TIME),
                productId = optString(RawJson.PRODUCT_ID),
                dataSignature = dataSignature,
                originalJson = purchaseData
            )
        }
    }

}
