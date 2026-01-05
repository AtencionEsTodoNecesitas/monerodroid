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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Credentials
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NodeRpcClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 18081,
    private var username: String = "",
    private var password: String = ""
) {
    companion object {
        private const val TAG = "NodeRpcClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val gson = Gson()

    // Create a trust-all client for local connections (self-signed certs)
    private val okHttpClient: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun getRpcUrl(): String = "https://$host:$port/json_rpc"

    private fun getHttpRpcUrl(): String = "http://$host:$port/json_rpc"

    fun setCredentials(username: String, password: String) {
        this.username = username
        this.password = password
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

        // Try HTTPS first, then HTTP
        val urls = listOf(getRpcUrl(), getHttpRpcUrl())

        for (url in urls) {
            try {
                val httpRequestBuilder = Request.Builder()
                    .url(url)
                    .post(requestBody)

                // Add authentication if credentials are set
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    httpRequestBuilder.header("Authorization", Credentials.basic(username, password))
                }

                val httpRequest = httpRequestBuilder.build()

                val response = okHttpClient.newCall(httpRequest).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "RPC Response from $url: $responseBody")

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val type = TypeToken.getParameterized(RpcResponse::class.java, T::class.java).type
                    return gson.fromJson(responseBody, type)
                }
            } catch (e: Exception) {
                Log.d(TAG, "RPC call to $url failed: ${e.message}")
                continue
            }
        }

        throw Exception("Failed to connect to node RPC")
    }
}
