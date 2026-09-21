package ir.marketbilling.constant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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

        // AAPT infers a type for manifest meta-data values that look numeric or
        // boolean (a version-gate digit string or "true"/"false" is typed as an
        // Integer/Boolean, not a String). Bundle.getString() on such an entry returns
        // null because the stored type doesn't match - it does NOT stringify the
        // value. Reading through the untyped Bundle.get() and calling toString()
        // normalises every supported type back to the string resolve() already parses
        // correctly. Do not "simplify" this back to getString() - that silently
        // reintroduces null reads for every numeric or boolean market meta-data key
        // (receiver/feature-config gates, subscription and trial capability flags).
        @Suppress("DEPRECATION")
        private fun Bundle?.stringValue(key: String): String? {
            return this?.get(key)?.toString()
        }

        private fun readMetaData(context: Context): MarketConfig {
            val config = try {
                val metaData = context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                resolve(
                    marketId = metaData.stringValue(META_DATA_MARKET_ID),
                    bindAddress = metaData.stringValue(META_DATA_BIND_ADDRESS),
                    hash = metaData.stringValue(META_DATA_HASH),
                    receiverClass = metaData.stringValue(META_DATA_RECEIVER_CLASS),
                    receiverMinVersion = metaData.stringValue(META_DATA_RECEIVER_MIN_VERSION),
                    featureConfigMinVersion = metaData
                        .stringValue(META_DATA_FEATURE_CONFIG_MIN_VERSION),
                    supportsSubscription = metaData.stringValue(META_DATA_SUPPORTS_SUBSCRIPTION),
                    supportsTrial = metaData.stringValue(META_DATA_SUPPORTS_TRIAL)
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
            logResolvedConfig(config)
            return config
        }

        // Runs once per process (readMetaData is only ever called from the cached
        // from(context) accessor). This is deliberately the only way to verify the
        // AAPT round-trip fix on a real device: Robolectric cannot reproduce AAPT's
        // Integer/Boolean typing of numeric- or boolean-looking meta-data values, so
        // no unit test can exercise this path. A device tester greps logcat for this
        // line and checks the two version gates are real numbers (not
        // 9223372036854775807) and the two capability flags are the expected
        // true/false, rather than everything having silently failed closed.
        private fun logResolvedConfig(config: MarketConfig) {
            Log.i(
                TAG,
                "MarketConfig resolved: packageName=${config.packageName}, " +
                    "bindAddress=${config.bindAddress}, " +
                    "hash=${if (config.signatureHash != null) "present" else "absent"}, " +
                    "receiverComponentName=${config.receiverComponentName}, " +
                    "receiverConnectionMinVersion=${config.receiverConnectionMinVersion}, " +
                    "featureConfigMinVersion=${config.featureConfigMinVersion}, " +
                    "supportsSubscription=${config.supportsSubscription}, " +
                    "supportsTrialSubscription=${config.supportsTrialSubscription}"
            )
        }
    }
}
