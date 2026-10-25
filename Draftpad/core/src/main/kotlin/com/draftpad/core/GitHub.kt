package com.draftpad.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

/** Minimal HTTP transport, so tests can stand in for GitHub. */
fun interface Http {
    fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Response

    class Response(val status: Int, val body: String)
}

/** [Http] over HttpURLConnection. Android's implementation (OkHttp) also allows PATCH; the JDK's doesn't. */
object UrlConnectionHttp : Http {
    override fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Http.Response {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.useCaches = false
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else c.inputStream
            return Http.Response(status, stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        } finally {
            c.disconnect()
        }
    }
}

class GitHubException(message: String, val status: Int = 0) : IOException(message)

/**
 * Pushes files to a GitHub repository with the REST API's Git database endpoints, so everything a
 * sketch needs lands in a single commit: blobs, then a tree on top of the branch's tree, then a commit,
 * then the branch moves to it. [token] is a fine-grained personal access token with
 * "Contents: Read and write" on the repository.
 */
class GitHub(private val token: String, private val http: Http = UrlConnectionHttp, private val api: String = API) {

    data class Repo(val owner: String, val name: String) {
        override fun toString() = "$owner/$name"

        companion object {
            private val PART = Regex("[A-Za-z0-9_.-]+")

            /** Reads "owner/name" or a github.com link to the repository. */
            fun parse(text: String): Repo? {
                val path = text.trim().removeSuffix("/").removeSuffix(".git")
                    .replace(Regex("^(https?://)?(www\\.)?github\\.com/", RegexOption.IGNORE_CASE), "")
                val parts = path.split('/')
                if (parts.size != 2 || parts.any { !PART.matches(it) || it == "." || it == ".." }) return null
                return Repo(parts[0], parts[1])
            }
        }
    }

    class Info(val fullName: String, val defaultBranch: String, val canPush: Boolean)

    class Pushed(val sha: String, val url: String, val branch: String)

    fun info(repo: Repo): Info {
        val m = obj(call("GET", "/repos/$repo", notFound = "Repository $repo not found, or the token can't see it"))
        // Only a definite "no" counts: some tokens get no permissions block at all.
        val permissions = m["permissions"] as? Map<*, *>
        return Info(
            m["full_name"] as? String ?: repo.toString(),
            m["default_branch"] as? String ?: "main",
            permissions?.get("push") != false,
        )
    }

    /** Whether [path] (a file or folder) exists on [branch]. */
    fun exists(repo: Repo, branch: String, path: String): Boolean {
        val res = send("GET", "/repos/$repo/contents/${encodePath(path)}?ref=${encode(branch)}", null)
        return when (res.status) {
            in 200..299 -> true
            404 -> false
            else -> throw failure(res, null)
        }
    }

    /** Adds or replaces [files] (repository path to content) on [branch] in one commit. */
    fun commit(repo: Repo, branch: String, files: List<Pair<String, ByteArray>>, message: String): Pushed {
        val blobs = files.map { (path, bytes) -> path to blob(repo, bytes) }
        val ref = "/repos/$repo/git/refs/heads/${encodePath(branch)}"
        var attempt = 1
        while (true) {
            val head = field(obj(call("GET", "/repos/$repo/git/ref/heads/${encodePath(branch)}", notFound = "Branch '$branch' not found in $repo"))["object"], "sha")
            val baseTree = field(obj(call("GET", "/repos/$repo/git/commits/$head"))["tree"], "sha")
            val entries = blobs.map { (path, sha) -> mapOf("path" to path, "mode" to "100644", "type" to "blob", "sha" to sha) }
            val tree = field(call("POST", "/repos/$repo/git/trees", mapOf("base_tree" to baseTree, "tree" to entries)), "sha")
            val created = obj(call("POST", "/repos/$repo/git/commits", mapOf("message" to message, "tree" to tree, "parents" to listOf(head))))
            val sha = field(created, "sha")
            val res = send("PATCH", ref, mapOf("sha" to sha, "force" to false))
            if (res.status in 200..299) {
                return Pushed(sha, created["html_url"] as? String ?: "https://github.com/$repo/commit/$sha", branch)
            }
            // 422: the branch moved while the commit was being made. Make it again on the new head.
            if (res.status != 422 || attempt++ == 3) throw failure(res, null)
        }
    }

    private fun blob(repo: Repo, bytes: ByteArray): String = field(
        call("POST", "/repos/$repo/git/blobs", mapOf("content" to Base64.getEncoder().encodeToString(bytes), "encoding" to "base64")),
        "sha",
    )

    private fun send(method: String, path: String, body: Any?): Http.Response {
        val headers = linkedMapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "Draftpad",
        )
        val bytes = body?.let { Json.write(it).toByteArray() }
        if (bytes != null) headers["Content-Type"] = "application/json; charset=utf-8"
        return try {
            http.send(method, api + path, headers, bytes)
        } catch (e: IOException) {
            throw GitHubException("Couldn't reach GitHub (${e.message ?: e.javaClass.simpleName}). Check your connection.")
        }
    }

    private fun call(method: String, path: String, body: Any? = null, notFound: String? = null): Any? {
        val res = send(method, path, body)
        if (res.status !in 200..299) throw failure(res, notFound)
        return if (res.body.isBlank()) null else Json.parse(res.body)
    }

    private fun failure(res: Http.Response, notFound: String?): GitHubException {
        val detail = runCatching { (Json.parse(res.body) as? Map<*, *>)?.get("message") as? String }.getOrNull()
        val message = when (res.status) {
            401 -> "GitHub didn't accept the token. It may have expired or been revoked."
            403, 429 -> if (detail?.contains("rate limit", ignoreCase = true) == true) {
                "GitHub's rate limit was reached. Try again in a few minutes."
            } else {
                "The token isn't allowed to write to this repository. It needs \"Contents: Read and write\" on it."
            }
            404 -> notFound ?: "Not found on GitHub."
            409 -> "The repository is empty. Add a first commit on GitHub (a README will do), then try again."
            else -> "GitHub answered ${res.status}."
        }
        val withDetail = if (detail != null && res.status != 401 && res.status != 409) "$message ($detail)" else message
        return GitHubException(withDetail, res.status)
    }

    private fun obj(v: Any?): Map<*, *> = v as? Map<*, *> ?: throw GitHubException("Unexpected answer from GitHub.")

    private fun field(v: Any?, key: String): String =
        (v as? Map<*, *>)?.get(key) as? String ?: throw GitHubException("Unexpected answer from GitHub (no $key).")

    companion object {
        const val API = "https://api.github.com"

        private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private fun encodePath(path: String): String = path.split('/').joinToString("/") { encode(it) }

        /**
         * GitHub's page for creating a fine-grained token, filled in for Draftpad: its name, a
         * description, the owner of [repo] as the resource owner and Contents: Read and write. The
         * repository itself still has to be picked on that page.
         */
        fun newTokenUrl(repo: Repo?): String {
            val owner = repo?.let { "&target_name=${encode(it.owner)}" } ?: ""
            val description = encode("Lets Draftpad push sketches" + (repo?.let { " to $it" } ?: ""))
            return "https://github.com/settings/personal-access-tokens/new?name=Draftpad&description=$description" +
                "$owner&expires_in=366&contents=write"
        }
    }
}
