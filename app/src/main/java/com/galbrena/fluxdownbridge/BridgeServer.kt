package com.galbrena.fluxdownbridge

import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class BridgeServer(private val token: String) {
    companion object {
        private const val TAG = "FluxDownBridge"
        private const val APP_VERSION = BuildConfig.VERSION_NAME
        const val PORT = 17800
        private const val CLIENT_THREADS = 12
        private const val SOCKET_READ_TIMEOUT_MS = 3_000
        private const val MAX_HEADER = 32 * 1024
        private const val MAX_BODY = 2 * 1024 * 1024
    }

    private val running = AtomicBoolean(false)
    private val nextClientThread = AtomicInteger()
    private val activeClients = AtomicInteger()
    private val clients: ExecutorService = Executors.newFixedThreadPool(
        CLIENT_THREADS,
    ) { runnable ->
        Thread(runnable, "fluxdown-client-${nextClientThread.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(
                InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT),
                50,
            )
        }
        acceptThread = Thread(::acceptLoop, "fluxdown-bridge").also { it.start() }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        clients.shutdownNow()
    }

    private fun acceptLoop() {
        while (running.get()) {
            try {
                val socket = serverSocket?.accept() ?: break
                try {
                    clients.execute { handle(socket) }
                } catch (error: RuntimeException) {
                    runCatching { socket.close() }
                    if (running.get()) {
                        Log.w(TAG, "accept dispatch failed: ${error.javaClass.simpleName}")
                    }
                }
            } catch (error: IOException) {
                if (running.get()) {
                    Log.w(TAG, "accept failed: ${error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        val startedAt = SystemClock.elapsedRealtime()
        var method = "unknown"
        var path = "unknown"
        var status = 400
        var requestParsed = false
        val active = activeClients.incrementAndGet()
        try {
            socket.use { connection ->
                connection.soTimeout = SOCKET_READ_TIMEOUT_MS
                connection.tcpNoDelay = true
                val request = readRequest(connection.getInputStream())
                    ?: throw IOException("invalid HTTP request")
                method = request.method
                path = request.path
                requestParsed = true
                val response = when {
                    request.method == "OPTIONS" -> HttpResponse(204, "{}")
                    request.method == "GET" && path == "/ping" -> {
                        val json = JSONObject()
                            .put("success", true)
                            .put("message", "pong")
                            .put("app", "FluxDown Android Bridge")
                            .put("version", APP_VERSION)
                        HttpResponse(200, json.toString())
                    }

                    request.method == "GET" && path == "/api/v1/info" -> {
                        if (authorized(request)) {
                            ok("ready")
                        } else {
                            HttpResponse(401, error("remote_auth_failed"))
                        }
                    }

                    request.method == "POST" && path == "/download" -> handleOne(request)
                    request.method == "POST" && path == "/download/batch" -> handleBatch(request)
                    else -> HttpResponse(404, error("not found"))
                }
                status = response.status
                writeResponse(connection.getOutputStream(), response, request.origin)
            }
        } catch (error: Exception) {
            if (requestParsed) {
                Log.w(
                    TAG,
                    "request failed class=${error.javaClass.simpleName} message=${error.message}",
                )
            }
            runCatching {
                writeResponse(
                    socket.getOutputStream(),
                    HttpResponse(400, error(error.message)),
                    null,
                )
            }
        } finally {
            activeClients.decrementAndGet()
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            if (requestParsed || durationMs >= SOCKET_READ_TIMEOUT_MS - 100) {
                Log.i(
                    TAG,
                    "request method=${safeLogValue(method)}" +
                        " path=${safeLogValue(path)}" +
                        " status=$status" +
                        " durationMs=$durationMs" +
                        " activeClients=$active",
                )
            }
        }
    }

    private fun handleOne(request: HttpRequest): HttpResponse {
        if (!authorized(request)) return HttpResponse(401, error("remote_auth_failed"))
        return try {
            launch(JSONObject(request.body))
            ok("sent to FluxDown")
        } catch (error: Exception) {
            HttpResponse(400, error(error.message))
        }
    }

    private fun handleBatch(request: HttpRequest): HttpResponse {
        if (!authorized(request)) return HttpResponse(401, error("remote_auth_failed"))
        return try {
            val root = JSONObject(request.body)
            val items: JSONArray = root.optJSONArray("items")
                ?: throw IllegalArgumentException("items array required")
            if (items.length() == 0) {
                throw IllegalArgumentException("items array required")
            }
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index)
                    ?: throw IllegalArgumentException("item $index is not an object")
                launch(item)
                if (index + 1 < items.length()) Thread.sleep(250)
            }
            ok("${items.length()} item(s) sent to FluxDown")
        } catch (error: Exception) {
            HttpResponse(400, error(error.message))
        }
    }

    private fun launch(item: JSONObject) {
        val url = item.optString("url", "").trim()
        if (url.isEmpty()) throw IllegalArgumentException("missing url")
        val scheme = runCatching { URI(url).scheme.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault("")
        if (scheme !in setOf("http", "https", "ftp", "magnet", "ed2k")) {
            throw IllegalArgumentException("unsupported URL scheme: $scheme")
        }
        if (item.optString("audioUrl", "").isNotEmpty()) {
            throw IllegalArgumentException(
                "separate audioUrl is not supported by protocol handoff",
            )
        }

        val uri = StringBuilder("fluxdown://download?url=")
            .append(encode(url))
            .also {
                append(it, "filename", item.optString("filename", ""))
                append(it, "cookies", item.optString("cookies", ""))
                append(it, "referrer", item.optString("referrer", ""))
                item.optJSONObject("headers")
                    ?.takeIf { headers -> headers.length() > 0 }
                    ?.let { headers -> append(it, "headers", headers.toString()) }
            }

        val result = RootLauncher.openFluxDown(uri.toString())
        if (!result.ok()) {
            throw IllegalStateException("root launch failed: ${result.output}")
        }
    }

    private fun append(uri: StringBuilder, key: String, value: String) {
        if (value.isEmpty()) return
        uri.append('&').append(key).append('=').append(encode(value))
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun safeLogValue(value: String): String {
        val cleaned = value
            .replace('\r', '_')
            .replace('\n', '_')
            .replace('\t', '_')
        return cleaned.take(80)
    }

    private fun authorized(request: HttpRequest): Boolean {
        val supplied = request.headers["x-fluxdown-token"] ?: return false
        return MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            token.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun ok(message: String): HttpResponse {
        val json = JSONObject()
            .put("success", true)
            .put("message", message)
        return HttpResponse(200, json.toString())
    }

    private fun error(message: String?): String =
        JSONObject()
            .put("success", false)
            .put("message", message ?: "error")
            .toString()

    private fun readRequest(input: InputStream): HttpRequest? {
        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (headerBytes.size() < MAX_HEADER) {
            val byte = input.read()
            if (byte < 0) throw IOException("unexpected EOF")
            headerBytes.write(byte)
            when {
                (state == 0 || state == 2) && byte == '\r'.code -> state++
                (state == 1 || state == 3) && byte == '\n'.code -> {
                    state++
                    if (state == 4) break
                }

                else -> state = if (byte == '\r'.code) 1 else 0
            }
        }
        val raw = headerBytes.toString(StandardCharsets.ISO_8859_1.name())
        val lines = raw.split("\r\n")
        val first = lines.firstOrNull()?.split(" ").orEmpty()
        if (first.size < 3) return null
        val request = HttpRequest(
            method = first[0],
            path = first[1].split("?", limit = 2)[0],
        )
        for (index in 1 until lines.size) {
            val line = lines[index]
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            request.headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] =
                line.substring(colon + 1).trim()
        }
        request.origin = request.headers["origin"]
        val length = request.headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) throw IOException("bad request size")
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) throw IOException("unexpected EOF")
            offset += count
        }
        request.body = body.toString(StandardCharsets.UTF_8)
        return request
    }

    private fun writeResponse(
        output: OutputStream,
        response: HttpResponse,
        origin: String?,
    ) {
        val body = response.body.toByteArray(StandardCharsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ")
            append(response.status)
            append(' ')
            append(reason(response.status))
            append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ")
            append(body.size)
            append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: ")
            append(origin ?: "*")
            append("\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append(
                "Access-Control-Allow-Headers: " +
                    "Content-Type, X-FluxDown-Token, X-FluxDown-Client\r\n",
            )
            append("Vary: Origin\r\n\r\n")
        }
        output.write(head.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        else -> "Response"
    }

    private data class HttpRequest(
        var method: String,
        var path: String,
        var origin: String? = null,
        var body: String = "",
        val headers: MutableMap<String, String> = hashMapOf(),
    )

    private data class HttpResponse(
        val status: Int,
        val body: String,
    )
}
