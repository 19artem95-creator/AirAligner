package com.example.airaligner

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class DeviceStatus(
    val signalDbm: Int,
    val chain0Dbm: Int,
    val chain1Dbm: Int,
    val cinr: Int,
    val isConnected: Boolean
)

class AirOsClient(private val ipAddress: String = "192.168.1.20") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    suspend fun fetchStatus(): DeviceStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$ipAddress/status.cgi")
            .header("User-Agent", "AirAlignerApp")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext disconnectedStatus()
                
                val body = response.body?.string() ?: return@withContext disconnectedStatus()
                val json = JsonParser.parseString(body).asJsonObject
                
                val wireless = json.getAsJsonObject("wireless") ?: return@withContext disconnectedStatus()
                val signal = wireless.get("signal")?.asInt ?: -95
                
                // Извлечение чейнов (Chain 0 / Chain 1)
                val chains = wireless.getAsJsonArray("chain_rssi")
                val c0 = chains?.get(0)?.asInt ?: signal
                val c1 = chains?.get(1)?.asInt ?: signal
                val cinr = wireless.get("cinr")?.asInt ?: 0

                DeviceStatus(
                    signalDbm = signal,
                    chain0Dbm = c0,
                    chain1Dbm = c1,
                    cinr = cinr,
                    isConnected = true
                )
            }
        } catch (e: Exception) {
            disconnectedStatus()
        }
    }

    private fun disconnectedStatus() = DeviceStatus(-99, -99, -99, 0, false)
}
