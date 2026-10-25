package com.draftpad.app

import com.draftpad.core.Http
import com.draftpad.core.Json
import java.util.Base64

/** Answers the GitHub endpoints a push uses, and keeps what was pushed: path to content. */
class RecordingGitHub(private val failWith: Int = 0) : Http {
    val pushed = linkedMapOf<String, ByteArray>()
    val calls = mutableListOf<String>()
    private val blobs = mutableMapOf<String, ByteArray>()

    override fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Http.Response {
        val path = url.substringAfter("api.github.com")
        calls += "$method $path"
        if (failWith != 0) return Http.Response(failWith, "{\"message\":\"Bad credentials\"}")
        val json = body?.let { Json.parse(String(it)) as Map<*, *> }
        fun ok(vararg pairs: Pair<String, Any?>) = Http.Response(200, Json.write(mapOf(*pairs)))
        return when {
            method == "GET" && path == "/repos/mfren022/blah" ->
                ok("full_name" to "mfren022/blah", "default_branch" to "master", "permissions" to mapOf("push" to true))
            method == "GET" && "/contents/" in path -> Http.Response(404, "{\"message\":\"Not Found\"}")
            method == "POST" && path.endsWith("/git/blobs") -> {
                val sha = "blob${blobs.size}"
                blobs[sha] = Base64.getDecoder().decode(json!!["content"] as String)
                ok("sha" to sha)
            }
            method == "GET" && "/git/ref/heads/" in path -> ok("object" to mapOf("sha" to "head"))
            method == "GET" && "/git/commits/" in path -> ok("sha" to "head", "tree" to mapOf("sha" to "tree0"))
            method == "POST" && path.endsWith("/git/trees") -> {
                for (entry in json!!["tree"] as List<*>) {
                    entry as Map<*, *>
                    pushed[entry["path"] as String] = blobs.getValue(entry["sha"] as String)
                }
                ok("sha" to "tree1")
            }
            method == "POST" && path.endsWith("/git/commits") ->
                ok("sha" to "commit1", "html_url" to "https://github.com/mfren022/blah/commit/commit1")
            method == "PATCH" && "/git/refs/heads/" in path -> ok("object" to mapOf("sha" to "commit1"))
            else -> Http.Response(500, "{\"message\":\"Unexpected $method $path\"}")
        }
    }
}
