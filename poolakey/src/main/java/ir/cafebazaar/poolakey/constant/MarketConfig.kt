package ir.cafebazaar.poolakey.constant

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

internal data class MarketConfig(
    val packageName: String,
    val bindAddress: String,
    val signatureHash: String?,
    val receiverConnectionMinVersion: Long,
    val featureConfigMinVersion: Long,
    val receiverComponentName: String?,
    val supportsSubscription: Boolean,
    val supportsTrialSubscription: Boolean
) {

    companion object {

        private const val TAG = "Poolakey"
        private const val META_DATA_MARKET_ID = "market_id"
        private const val META_DATA_BIND_ADDRESS = "market_bind"
        private const val META_DATA_HASH = "market_hash"
        private const val META_DATA_RECEIVER_CLASS = "market_receiver"
        private const val META_DATA_RECEIVER_MIN_VERSION = "market_receiver_min"
        private const val META_DATA_FEATURE_CONFIG_MIN_VERSION = "market_feature_min"
        private const val META_DATA_SUPPORTS_SUBSCRIPTION = "market_subs"
        private const val META_DATA_SUPPORTS_TRIAL = "market_trial"

        @Volatile
        private var cached: MarketConfig? = null

        fun from(context: Context): MarketConfig {
            return cached ?: synchronized(this) {
                cached ?: readMetaData(context).also { cached = it }
            }
        }

        /**
         * Resolves a [MarketConfig] purely from the raw meta-data values supplied by the
         * host manifest. This function must never branch on market identity: every value
         * either arrives from the caller or falls back to a market-independent default.
         */
        fun resolve(
            marketId: String?,
            bindAddress: String?,
            hash: String?,
            receiverClass: String?,
            receiverMinVersion: String?,
            featureConfigMinVersion: String?,
            supportsSubscription: String?,
            supportsTrial: String?
        ): MarketConfig {
            return MarketConfig(
                packageName = marketId.orBlankToEmpty(),
                bindAddress = bindAddress.orBlankToEmpty(),
                signatureHash = hash?.takeIf { it.isNotBlank() },
                receiverConnectionMinVersion = receiverMinVersion.toVersionOrFailClosed(),
                featureConfigMinVersion = featureConfigMinVersion.toVersionOrFailClosed(),
                receiverComponentName = receiverClass?.takeIf { it.isNotBlank() },
                supportsSubscription = supportsSubscription.toSafeBoolean(),
                supportsTrialSubscription = supportsTrial.toSafeBoolean()
            )
        }

        private fun String?.orBlankToEmpty(): String {
            return this?.takeIf { it.isNotBlank() } ?: ""
        }

        private fun String?.toVersionOrFailClosed(): Long {
            return this?.trim()?.toLongOrNull() ?: Long.MAX_VALUE
        }

        private fun String?.toSafeBoolean(): Boolean {
            return this?.equals("true", ignoreCase = true) ?: false
        }

        private fun readMetaData(context: Context): MarketConfig {
            return try {
                val metaData = context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                resolve(
                    marketId = metaData?.getString(META_DATA_MARKET_ID),
                    bindAddress = metaData?.getString(META_DATA_BIND_ADDRESS),
                    hash = metaData?.getString(META_DATA_HASH),
                    receiverClass = metaData?.getString(META_DATA_RECEIVER_CLASS),
                    receiverMinVersion = metaData?.getString(META_DATA_RECEIVER_MIN_VERSION),
                    featureConfigMinVersion = metaData
                        ?.getString(META_DATA_FEATURE_CONFIG_MIN_VERSION),
                    supportsSubscription = metaData?.getString(META_DATA_SUPPORTS_SUBSCRIPTION),
                    supportsTrial = metaData?.getString(META_DATA_SUPPORTS_TRIAL)
                )
            } catch (exception: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Could not read market meta-data.", exception)
                resolve(
                    marketId = null,
                    bindAddress = null,
                    hash = null,
                    receiverClass = null,
                    receiverMinVersion = null,
                    featureConfigMinVersion = null,
                    supportsSubscription = null,
                    supportsTrial = null
                )
            }
        }
    }
}
