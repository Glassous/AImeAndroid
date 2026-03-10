package com.glassous.aime.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

object LocationUtils {

    /**
     * 获取当前设备的最后已知位置
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(context: Context): Location? {
        return withContext(Dispatchers.IO) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = locationManager.getProviders(true)
                var bestLocation: Location? = null
                for (provider in providers) {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                }
                bestLocation
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 将经纬度转换为地理位置字符串
     *
     * @param context 上下文
     * @param latitude 纬度
     * @param longitude 经度
     * @return 格式化后的地址字符串，超时或异常时返回兜底字符串
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        val fallbackResult = "未知地点(仅包含经纬度: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})"

        return try {
            withTimeoutOrNull(2000L) {
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13 (API 33) 及以上使用异步 API
                        suspendCancellableCoroutine { continuation ->
                            val geocoder = Geocoder(context, Locale.getDefault())
                            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    val address = addresses.firstOrNull()
                                    continuation.resume(formatAddress(address) ?: fallbackResult)
                                }

                                override fun onError(errorMessage: String?) {
                                    continuation.resume(fallbackResult)
                                }
                            })
                        }
                    } else {
                        // 旧版本使用同步 API
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        formatAddress(addresses?.firstOrNull()) ?: fallbackResult
                    }
                }
            } ?: fallbackResult
        } catch (e: Exception) {
            // 捕获所有可能的异常，如 IOException, IllegalArgumentException 等
            fallbackResult
        }
    }

    /**
     * 格式化地址信息，提取省、市、区、街道等信息并去重拼接
     */
    private fun formatAddress(address: Address?): String? {
        if (address == null) return null

        val parts = mutableListOf<String>()
        
        // 依次获取各个层级的地址信息
        address.adminArea?.let { parts.add(it) }       // 省/直辖市
        address.locality?.let { parts.add(it) }        // 市
        address.subLocality?.let { parts.add(it) }     // 区/县
        address.thoroughfare?.let { parts.add(it) }    // 街道/路
        address.featureName?.let { 
            // 如果 featureName 和前面的信息重复，则不添加
            if (it != address.thoroughfare && it != address.subLocality) {
                parts.add(it)
            }
        }

        // 去重并按顺序拼接
        val uniqueParts = parts.distinct().filter { it.isNotBlank() }
        
        return if (uniqueParts.isEmpty()) {
            address.getAddressLine(0) // 兜底返回完整地址行
        } else {
            uniqueParts.joinToString("")
        }
    }
}
