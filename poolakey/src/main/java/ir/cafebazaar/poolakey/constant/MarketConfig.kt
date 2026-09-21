package ir.cafebazaar.poolakey.constant

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import ir.cafebazaar.poolakey.BuildConfig

internal data class MarketConfig(
    val packageName: String,
    val bindAddress: String,
    val signatureHash: String?,
    val receiverConnectionMinVersion: Long
) {

    companion object {

        private const val TAG = "Poolakey"
        private const val META_DATA_MARKET_ID = "market_id"
        private const val META_DATA_BIND_ADDRESS = "market_bind"

        @Volatile
        private var cached: MarketConfig? = null

        fun from(context: Context): MarketConfig {
            return cached ?: synchronized(this) {
                cached ?: readMetaData(context).also { cached = it }
            }
        }

        fun resolve(marketId: String?, bindAddress: String?): MarketConfig {
            val resolvedId = marketId?.takeIf { it.isNotBlank() }
                ?: Const.BAZAAR_PACKAGE_NAME
            val suppliedBind = bindAddress?.takeIf { it.isNotBlank() }

            return when (resolvedId) {
                Const.BAZAAR_PACKAGE_NAME -> MarketConfig(
                    packageName = resolvedId,
                    bindAddress = suppliedBind ?: Const.BAZAAR_BIND_ADDRESS,
                    signatureHash = BuildConfig.BAZAAR_HASH,
                    receiverConnectionMinVersion =
                        Const.BAZAAR_RECEIVER_CONNECTION_MIN_VERSION
                )
                Const.MYKET_PACKAGE_NAME -> MarketConfig(
                    packageName = resolvedId,
                    bindAddress = suppliedBind ?: Const.MYKET_BIND_ADDRESS,
                    signatureHash = BuildConfig.MYKET_HASH,
                    receiverConnectionMinVersion = Long.MAX_VALUE
                )
                else -> {
                    Log.w(TAG, "Unknown market '$resolvedId'; refusing to connect.")
                    MarketConfig(
                        packageName = resolvedId,
                        bindAddress = suppliedBind ?: Const.BAZAAR_BIND_ADDRESS,
                        signatureHash = null,
                        receiverConnectionMinVersion = Long.MAX_VALUE
                    )
                }
            }
        }

        private fun readMetaData(context: Context): MarketConfig {
            return try {
                val metaData = context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                resolve(
                    marketId = metaData?.getString(META_DATA_MARKET_ID),
                    bindAddress = metaData?.getString(META_DATA_BIND_ADDRESS)
                )
            } catch (exception: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Could not read market meta-data; defaulting to Bazaar.", exception)
                resolve(marketId = null, bindAddress = null)
            }
        }
    }
}
