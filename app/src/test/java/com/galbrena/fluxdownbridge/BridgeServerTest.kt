package com.galbrena.fluxdownbridge

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class BridgeServerTest {
    private val launches = CopyOnWriteArrayList<String>()
    private var failLaunch = 0
    private val server = BridgeServer("old-token") { uri ->
        launches.add(uri)
        if (launches.size == failLaunch) RootLauncher.Result(1, "fixture failure")
        else RootLauncher.Result(0, "OK")
    }.also { it.start(0) }

    @After fun close() = server.stop()

    private fun request(path: String, token: String? = null, body: String? = null): Pair<Int, JSONObject> {
        Socket("127.0.0.1", server.localPort).use { socket ->
            socket.soTimeout = 4_000
            val payload = body?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
            val headers = buildString {
                append(if (body == null) "GET" else "POST")
                append(" $path HTTP/1.1\r\nHost: localhost\r\n")
                if (token != null) append("X-FluxDown-Token: $token\r\n")
                append("Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n")
            }
            socket.getOutputStream().apply { write(headers.toByteArray()); write(payload); flush() }
            val raw = socket.getInputStream().bufferedReader().readText()
            return raw.substringBefore("\r\n").split(" ")[1].toInt() to
                JSONObject(raw.substringAfter("\r\n\r\n"))
        }
    }

    @Test fun tokenReloadKeepsListenerAndRejectsPreviousToken() {
        val port = server.localPort
        assertEquals(200, request("/api/v1/info", "old-token").first)
        server.updateToken("new-token")
        assertEquals(port, server.localPort)
        assertEquals(401, request("/api/v1/info", "old-token").first)
        assertEquals(200, request("/api/v1/info", "new-token").first)
        assertEquals(200, request("/ping").first)
    }

    @Test fun bufferedReaderPreservesLargeUtf8BodyAndHeaderBoundary() {
        val filename = "下载".repeat(5_000) + ".zip"
        val body = JSONObject().put("url", "https://example.test/file.zip")
            .put("filename", filename).toString()
        assertEquals(200, request("/download", "old-token", body).first)
        val parsed = java.net.URLDecoder.decode(launches.single(), "UTF-8")
        assertTrue(parsed.contains("filename=$filename"))
    }

    @Test fun invalidLaterItemDoesNotLaunchEarlierItems() {
        val (status, json) = request("/download/batch", "old-token",
            """{"items":[{"url":"https://example.test/one"},{"url":"file:///invalid"}]}""")
        assertEquals(400, status)
        assertEquals(0, json.getInt("sent"))
        assertEquals(2, json.getInt("failedIndex"))
        assertTrue(launches.isEmpty())
    }

    @Test fun partialLaunchFailureReportsProgressAndStopsRemainingItems() {
        failLaunch = 2
        val (status, json) = request("/download/batch", "old-token",
            """{"items":[{"url":"https://example.test/one"},{"url":"https://example.test/two"},{"url":"https://example.test/three"}]}""")
        assertEquals(400, status)
        assertFalse(json.getBoolean("success"))
        assertEquals(1, json.getInt("sent"))
        assertEquals(3, json.getInt("total"))
        assertEquals(2, json.getInt("failedIndex"))
        assertEquals(2, launches.size)
    }

    @Test fun batchSuccessReportsAllSent() {
        val (status, json) = request("/download/batch", "old-token",
            """{"items":[{"url":"https://example.test/one"},{"url":"https://example.test/two"}]}""")
        assertEquals(200, status)
        assertEquals(2, json.getInt("sent"))
        assertTrue(json.getBoolean("success"))
        assertEquals(2, launches.size)
    }

    @Test fun idlePreconnectionsDoNotBlockPing() {
        val idle = List(8) { Socket("127.0.0.1", server.localPort) }
        try { assertEquals(200, request("/ping").first) }
        finally { idle.forEach { it.close() } }
    }

    @Test fun unauthenticatedDownloadDoesNotLaunch() {
        assertEquals(401, request("/download", body = """{"url":"https://example.test/one"}""").first)
        assertTrue(launches.isEmpty())
    }
}
