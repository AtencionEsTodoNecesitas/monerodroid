package com.sevendeuce.monerodroid.util

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP Proxy Server that forwards RPC requests to monerod.
 *
 * This server listens on port 8081 (configurable) and forwards all requests
 * to the local monerod RPC at 127.0.0.1:18081, handling Digest authentication
 * transparently so external clients don't need to worry about auth.
 *
 * External devices connect to: http://<phone-ip>:8081
 */
class RpcProxyServer(
    private val proxyPort: Int = 8081,
    private val targetHost: String = "127.0.0.1",
    private val targetPort: Int = 18081
) : NanoHTTPD(proxyPort) {

    companion object {
        private const val TAG = "RpcProxyServer"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private var username: String = ""
    private var password: String = ""
    private var okHttpClient: OkHttpClient? = null

    /**
     * Set RPC credentials for authenticating with monerod.
     * Must be called before starting the server.
     */
    fun setCredentials(username: String, password: String) {
        this.username = username
        this.password = password
        // Rebuild client with new credentials
        okHttpClient = buildClient()
        Log.d(TAG, "Proxy credentials set: user=$username")
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Add Digest authenticator if credentials are set
        if (username.isNotEmpty() && password.isNotEmpty()) {
            builder.authenticator(DigestAuthenticator(username, password))
        }

        return builder.build()
    }

    private fun getOrCreateClient(): OkHttpClient {
        val existingClient = okHttpClient
        if (existingClient != null) return existingClient
        return buildClient().also { okHttpClient = it }
    }

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri
        val remoteIp = session.remoteIpAddress

        Log.d(TAG, "Proxy request: $method $uri from $remoteIp")

        return try {
            when (method) {
                Method.GET -> handleGetRequest(session)
                Method.POST -> handlePostRequest(session)
                Method.OPTIONS -> handleOptionsRequest()
                else -> {
                    Log.d(TAG, "Unsupported method: $method")
                    newFixedLengthResponse(
                        Response.Status.METHOD_NOT_ALLOWED,
                        MIME_PLAINTEXT,
                        "Method not allowed"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proxy error", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Proxy error: ${e.message}"
            )
        }
    }

    private fun handleGetRequest(session: IHTTPSession): Response {
        val targetUrl = "http://$targetHost:$targetPort${session.uri}"

        // Build query string if present
        val queryString = session.queryParameterString
        val fullUrl = if (queryString.isNullOrEmpty()) targetUrl else "$targetUrl?$queryString"

        Log.d(TAG, "Forwarding GET to: $fullUrl")

        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .build()

        return executeAndRespond(request)
    }

    private fun handlePostRequest(session: IHTTPSession): Response {
        val targetUrl = "http://$targetHost:$targetPort${session.uri}"

        // Read request body
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer, 0, contentLength)
            String(buffer)
        } else {
            // Try reading without content-length
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            files["postData"] ?: ""
        }

        Log.d(TAG, "Forwarding POST to: $targetUrl, body length: ${body.length}")

        val contentType = session.headers["content-type"] ?: "application/json"
        val mediaType = contentType.toMediaType()

        val request = Request.Builder()
            .url(targetUrl)
            .post(body.toRequestBody(mediaType))
            .build()

        return executeAndRespond(request)
    }

    private fun handleOptionsRequest(): Response {
        // Handle CORS preflight requests
        val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        return response
    }

    private fun executeAndRespond(request: Request): Response {
        val client = getOrCreateClient()

        val okResponse = client.newCall(request).execute()
        val responseBody = okResponse.body?.string() ?: ""
        val contentType = okResponse.header("Content-Type") ?: "application/json"

        Log.d(TAG, "monerod response: ${okResponse.code}, body length: ${responseBody.length}")

        val status = when (okResponse.code) {
            200 -> Response.Status.OK
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN
            404 -> Response.Status.NOT_FOUND
            500 -> Response.Status.INTERNAL_ERROR
            else -> Response.Status.lookup(okResponse.code) ?: Response.Status.OK
        }

        val response = newFixedLengthResponse(status, contentType, responseBody)

        // Add CORS headers for browser access
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")

        return response
    }

    /**
     * Start the proxy server.
     */
    fun startProxy() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "RPC Proxy started on port $proxyPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy server", e)
            throw e
        }
    }

    /**
     * Stop the proxy server.
     */
    fun stopProxy() {
        try {
            stop()
            Log.d(TAG, "RPC Proxy stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proxy server", e)
        }
    }
}
