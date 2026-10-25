package com.draftpad.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The real transport against a local server. (PATCH only works on Android's HttpURLConnection.) */
class UrlConnectionHttpTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val seen = mutableListOf<String>()

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            seen += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")} $body"
            val (status, reply) = if (exchange.requestURI.path == "/missing") 404 to "{\"message\":\"Not Found\"}" else 200 to "{\"ok\":true}"
            val bytes = reply.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @After
    fun stop() = server.stop(0)

    @Test
    fun sendsHeadersAndBodiesAndReadsReplies() {
        val get = UrlConnectionHttp.send("GET", "$base/repos/a/b", mapOf("Authorization" to "Bearer t"), null)
        assertEquals(200, get.status)
        assertEquals("{\"ok\":true}", get.body)
        val post = UrlConnectionHttp.send("POST", "$base/repos/a/b/git/blobs", mapOf("Authorization" to "Bearer t"), "{\"x\":1}".toByteArray())
        assertEquals(200, post.status)
        assertEquals(listOf("GET /repos/a/b Bearer t ", "POST /repos/a/b/git/blobs Bearer t {\"x\":1}"), seen)
    }

    @Test
    fun readsErrorBodies() {
        val res = UrlConnectionHttp.send("GET", "$base/missing", emptyMap(), null)
        assertEquals(404, res.status)
        assertEquals("{\"message\":\"Not Found\"}", res.body)
    }

    @Test
    fun githubClientWorksOverIt() {
        val info = GitHub("t", UrlConnectionHttp, base).info(GitHub.Repo("a", "b"))
        // The local server's reply has none of the fields: the defaults apply.
        assertEquals("a/b", info.fullName)
        assertEquals(true, info.canPush)
    }
}
