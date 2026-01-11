package com.sevendeuce.monerodroid.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sevendeuce.monerodroid.data.GetInfoResult
import com.sevendeuce.monerodroid.data.RpcRequest
import com.sevendeuce.monerodroid.data.RpcResponse
import com.sevendeuce.monerodroid.data.SyncInfoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NodeRpcClient(
    private var host: String = "0.0.0.0",
    private var port: Int = 18081,
    private var username: String = "",
    private var password: String = ""
) {
    companion object {
        private const val TAG = "NodeRpcClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val gson = Gson()

    // Mutable client that gets rebuilt when credentials change
    private var okHttpClient: OkHttpClient? = null

    private fun getOrCreateClient(): OkHttpClient {
        val existingClient = okHttpClient
        if (existingClient != null) return existingClient

        return buildClient().also { okHttpClient = it }
    }

    private fun buildClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val builder = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)

        // Add Digest authenticator if credentials are set
        if (username.isNotEmpty() && password.isNotEmpty()) {
            builder.authenticator(DigestAuthenticator(username, password))
        }

        return builder.build()
    }

    private fun getRpcUrl(): String = "https://$host:$port/json_rpc"

    private fun getHttpRpcUrl(): String = "http://$host:$port/json_rpc"

    fun setCredentials(username: String, password: String) {
        this.username = username
        this.password = password
        // Rebuild client with new credentials for Digest auth
        okHttpClient = buildClient()
        Log.d(TAG, "Credentials set: user=$username, pass=${password.take(4)}***")
    }

    fun setHost(host: String, port: Int = this.port) {
        this.host = host
        this.port = port
        Log.d(TAG, "RPC host set to: $host:$port")
    }

    suspend fun getInfo(): Result<GetInfoResult> = withContext(Dispatchers.IO) {
        try {
            val request = RpcRequest(method = "get_info")
            val response = makeRpcCall<GetInfoResult>(request)

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else if (response.result != null) {
                Result.success(response.result)
            } else {
                Result.failure(Exception("Empty response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getInfo failed", e)
            Result.failure(e)
        }
    }

    suspend fun getSyncInfo(): Result<SyncInfoResult> = withContext(Dispatchers.IO) {
        try {
            val request = RpcRequest(method = "sync_info")
            val response = makeRpcCall<SyncInfoResult>(request)

            if (response.error != null) {
                Result.failure(Exception(response.error.message))
            } else if (response.result != null) {
                Result.success(response.result)
            } else {
                Result.failure(Exception("Empty response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSyncInfo failed", e)
            Result.failure(e)
        }
    }

    suspend fun isNodeRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = getInfo()
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun stopDaemon(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = RpcRequest(method = "stop_daemon")
            makeRpcCall<Any>(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "stopDaemon failed", e)
            Result.failure(e)
        }
    }

    private inline fun <reified T> makeRpcCall(request: RpcRequest): RpcResponse<T> {
        val jsonRequest = gson.toJson(request)
        Log.d(TAG, "RPC Request: $jsonRequest")

        val requestBody = jsonRequest.toRequestBody(JSON_MEDIA_TYPE)
        val client = getOrCreateClient()

        // Try HTTP first (monerod typically uses HTTP for local RPC), then HTTPS
        val urls = listOf(getHttpRpcUrl(), getRpcUrl())

        for (url in urls) {
            try {
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                Log.d(TAG, "RPC call to $url (auth=${username.isNotEmpty()})")

                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "RPC Response code=${response.code} from $url")

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val type = TypeToken.getParameterized(RpcResponse::class.java, T::class.java).type
                    return gson.fromJson(responseBody, type)
                } else if (response.code == 401) {
                    Log.d(TAG, "Auth failed - response: $responseBody")
                }
            } catch (e: Exception) {
                Log.d(TAG, "RPC call to $url failed: ${e.message}")
                continue
            }
        }

        throw Exception("Failed to connect to node RPC")
    }
}

/**
 * Digest Authentication implementation for OkHttp.
 * Monerod uses HTTP Digest Authentication for RPC calls.
 */
class DigestAuthenticator(
    private val username: String,
    private val password: String
) : Authenticator {

    companion object {
        private const val TAG = "DigestAuth"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if we already tried authentication
        if (response.request.header("Authorization") != null) {
            Log.d(TAG, "Already attempted auth, giving up")
            return null
        }

        val wwwAuthenticate = response.header("WWW-Authenticate") ?: return null
        Log.d(TAG, "WWW-Authenticate: $wwwAuthenticate")

        if (!wwwAuthenticate.startsWith("Digest", ignoreCase = true)) {
            Log.d(TAG, "Not Digest auth, skipping")
            return null
        }

        // Parse the Digest challenge
        val params = parseDigestChallenge(wwwAuthenticate)
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val qop = params["qop"]
        val opaque = params["opaque"]

        // Generate client nonce and nonce count
        val cnonce = generateCnonce()
        val nc = "00000001"

        // Calculate response
        val method = response.request.method
        val uri = response.request.url.encodedPath

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")

        val responseHash = if (qop != null) {
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        // Build Authorization header
        val authHeader = buildString {
            append("Digest username=\"$username\"")
            append(", realm=\"$realm\"")
            append(", nonce=\"$nonce\"")
            append(", uri=\"$uri\"")
            if (qop != null) {
                append(", qop=$qop")
                append(", nc=$nc")
                append(", cnonce=\"$cnonce\"")
            }
            append(", response=\"$responseHash\"")
            if (opaque != null) {
                append(", opaque=\"$opaque\"")
            }
        }

        Log.d(TAG, "Sending Digest auth for user: $username")

        return response.request.newBuilder()
            .header("Authorization", authHeader)
            .build()
    }

    private fun parseDigestChallenge(header: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        // Remove "Digest " prefix
        val content = header.substringAfter("Digest ", "").trim()

        // Parse key=value pairs
        val regex = """(\w+)=(?:"([^"]+)"|([^,\s]+))""".toRegex()
        regex.findAll(content).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            params[key] = value
        }

        return params
    }

    private fun generateCnonce(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
