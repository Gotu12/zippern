package com.zipper.datingapp.webrtc

import android.content.Context
import android.util.Log
import com.zipper.datingapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches TURN/credential ICE servers from [BuildConfig.BACKEND_ICE_URL] (HTTPS GET, JSON body).
 *
 * Supported shapes:
 * - `{ "iceServers": [ { "urls": "turn:...", "username": "...", "credential": "..." } ] }`
 * - `[ { "urls": [...] , ... } ]`
 *
 * On failure or empty URL, [IceServerCatalog] is updated to STUN-only so direct candidates still run.
 */
object IceConfigRepository {

    private const val TAG = "IceConfigRepository"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    suspend fun refresh(context: Context): Unit = withContext(Dispatchers.IO) {
        val urlStr = BuildConfig.BACKEND_ICE_URL.trim()
        if (urlStr.isEmpty()) {
            IceServerCatalog.updateFromBackend(emptyList())
            Log.d(TAG, "BACKEND_ICE_URL empty — using STUN only")
            return@withContext
        }
        try {
            val servers = fetchIceServersFromUrl(urlStr)
            IceServerCatalog.updateFromBackend(servers)
            Log.d(TAG, "ICE refresh OK: ${servers.size} server(s) from backend")
        } catch (e: Exception) {
            Log.w(TAG, "ICE refresh failed — STUN only", e)
            IceServerCatalog.updateFromBackend(emptyList())
        }
    }

    private fun fetchIceServersFromUrl(urlString: String): List<PeerConnection.IceServer> {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            useCaches = false
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("ICE HTTP $code $err")
            }
            conn.inputStream.bufferedReader().use { reader ->
                val body = reader.readText()
                return parseIceServersJson(body)
            }
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseIceServersJson(body: String): List<PeerConnection.IceServer> {
        val trimmed = body.trim()
        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val root = JSONObject(trimmed)
                when {
                    root.has("iceServers") -> root.getJSONArray("iceServers")
                    root.has("ice_servers") -> root.getJSONArray("ice_servers")
                    else -> JSONArray()
                }
            }
        }
        val out = ArrayList<PeerConnection.IceServer>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            iceServerFromJsonObject(o)?.let { out.add(it) }
        }
        return out
    }

    private fun iceServerFromJsonObject(o: JSONObject): PeerConnection.IceServer? {
        if (!o.has("urls")) return null
        val urlsJson = o.get("urls")
        val urls: List<String> = when (urlsJson) {
            is String -> listOf(urlsJson)
            is JSONArray -> List(urlsJson.length()) { idx -> urlsJson.getString(idx) }
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
        if (urls.isEmpty()) return null
        val user = o.optString("username", "").trim().takeIf { it.isNotEmpty() }
        val cred = o.optString("credential", "").trim().takeIf { it.isNotEmpty() }
            ?: o.optString("password", "").trim().takeIf { it.isNotEmpty() }
        val builder = PeerConnection.IceServer.builder(urls)
        if (user != null && cred != null) {
            builder.setUsername(user)
            builder.setPassword(cred)
        }
        return builder.createIceServer()
    }
}
