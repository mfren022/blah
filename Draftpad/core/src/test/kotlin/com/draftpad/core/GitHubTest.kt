package com.draftpad.core

import java.io.IOException
import java.net.URLDecoder
import java.time.ZoneOffset
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** An in-memory GitHub that answers the REST endpoints [GitHub] uses. */
class FakeGitHub(var canPush: Boolean = true) : Http {
    class Request(val method: String, val path: String, val headers: Map<String, String>, val body: Map<*, *>?)

    val requests = mutableListOf<Request>()
    val blobs = mutableMapOf<String, ByteArray>()
    val trees = mutableMapOf<String, Map<String, String>>() // tree sha to (path to blob sha)
    val commits = mutableMapOf<String, Pair<String, String?>>() // commit sha to (tree sha, parent)
    val messages = mutableMapOf<String, String>()
    val branches = mutableMapOf<String, String>()

    /** Runs once, just before the next branch update, to simulate someone else pushing first. */
    var beforeNextUpdate: (() -> Unit)? = null
    var failWith: Http.Response? = null

    init {
        trees["t0"] = mapOf("README.md" to "b0")
        blobs["b0"] = "hello".toByteArray()
        commits["c0"] = "t0" to null
        branches["master"] = "c0"
    }

    fun files(branch: String = "master"): Map<String, ByteArray> {
        val tree = trees.getValue(commits.getValue(branches.getValue(branch)).first)
        return tree.mapValues { blobs.getValue(it.value) }
    }

    /** Someone else adds a file on [branch]. */
    fun pushOther(branch: String, path: String) {
        val head = branches.getValue(branch)
        val tree = trees.getValue(commits.getValue(head).first) + (path to "b0")
        trees["t${trees.size}"] = tree
        val sha = "c${commits.size}"
        commits[sha] = "t${trees.size - 1}" to head
        branches[branch] = sha
    }

    private fun ok(body: Any?) = Http.Response(200, Json.write(body))
    private fun missing() = Http.Response(404, "{\"message\":\"Not Found\"}")

    override fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Http.Response {
        val path = url.removePrefix(GitHub.API)
        val json = body?.let { Json.parse(String(it)) as Map<*, *> }
        requests += Request(method, path, headers, json)
        failWith?.let { return it }
        val repo = "/repos/mfren022/blah"
        return when {
            method == "GET" && path == repo -> ok(mapOf("full_name" to "mfren022/blah", "default_branch" to "master", "permissions" to mapOf("push" to canPush)))
            method == "GET" && path.startsWith("$repo/contents/") -> {
                val p = URLDecoder.decode(path.removePrefix("$repo/contents/").substringBefore('?'), "UTF-8")
                val branch = URLDecoder.decode(path.substringAfter("?ref="), "UTF-8")
                if (branch !in branches) return missing()
                if (files(branch).keys.any { it == p || it.startsWith("$p/") }) ok(mapOf("path" to p)) else missing()
            }
            method == "POST" && path == "$repo/git/blobs" -> {
                assertEquals("base64", json!!["encoding"])
                val sha = "b${blobs.size}"
                blobs[sha] = Base64.getDecoder().decode(json["content"] as String)
                Http.Response(201, Json.write(mapOf("sha" to sha)))
            }
            method == "GET" && path.startsWith("$repo/git/ref/heads/") -> {
                val head = branches[path.removePrefix("$repo/git/ref/heads/")] ?: return missing()
                ok(mapOf("object" to mapOf("sha" to head, "type" to "commit")))
            }
            method == "GET" && path.startsWith("$repo/git/commits/") -> {
                val sha = path.removePrefix("$repo/git/commits/")
                ok(mapOf("sha" to sha, "tree" to mapOf("sha" to commits.getValue(sha).first)))
            }
            method == "POST" && path == "$repo/git/trees" -> {
                val base = trees.getValue(json!!["base_tree"] as String)
                val added = (json["tree"] as List<*>).associate { e -> e as Map<*, *>
                    assertEquals("100644", e["mode"])
                    e["path"] as String to e["sha"] as String
                }
                val sha = "t${trees.size}"
                trees[sha] = base + added
                Http.Response(201, Json.write(mapOf("sha" to sha)))
            }
            method == "POST" && path == "$repo/git/commits" -> {
                val sha = "c${commits.size}"
                commits[sha] = json!!["tree"] as String to (json["parents"] as List<*>).single() as String
                messages[sha] = json["message"] as String
                Http.Response(201, Json.write(mapOf("sha" to sha, "html_url" to "https://github.com/mfren022/blah/commit/$sha")))
            }
            method == "PATCH" && path.startsWith("$repo/git/refs/heads/") -> {
                beforeNextUpdate?.let { beforeNextUpdate = null; it() }
                val branch = path.removePrefix("$repo/git/refs/heads/")
                val sha = json!!["sha"] as String
                assertEquals(false, json["force"])
                if (commits.getValue(sha).second != branches[branch]) {
                    Http.Response(422, "{\"message\":\"Update is not a fast forward\"}")
                } else {
                    branches[branch] = sha
                    ok(mapOf("ref" to "refs/heads/$branch", "object" to mapOf("sha" to sha)))
                }
            }
            else -> Http.Response(500, "{\"message\":\"Unexpected $method $path\"}")
        }
    }
}

class GitHubTest {
    private val ink = Colors.parse("#1F1F1F")
    private val repo = GitHub.Repo("mfren022", "blah")
    private val picture = SketchPusher.Picture(byteArrayOf(1, 2, 3), 800, 600, Box(0f, 0f, 400f, 300f))
    private val draft = Draft(
        id = "20251025-140312",
        title = "Castle keep",
        notes = "Make the keep taller.",
        created = 1761400992000,
        modified = 1761401102000,
        elements = listOf(Shape(ShapeKind.RECT, 10f, 10f, 100f, 100f, ink, 4f), Label("Keep", 20f, 50f, 20f, ink)),
    )

    @Test
    fun parsesRepositories() {
        assertEquals(repo, GitHub.Repo.parse("mfren022/blah"))
        assertEquals(repo, GitHub.Repo.parse(" https://github.com/mfren022/blah.git "))
        assertEquals(repo, GitHub.Repo.parse("github.com/mfren022/blah/"))
        assertNull(GitHub.Repo.parse("mfren022"))
        assertNull(GitHub.Repo.parse("a/b/c"))
        assertNull(GitHub.Repo.parse("me/../x"))
        assertNull(GitHub.Repo.parse("me/my repo"))
    }

    @Test
    fun pushesASketchInOneCommit() {
        val gh = FakeGitHub()
        val result = SketchPusher(GitHub("secret", gh)).push(repo, null, "drafts", draft, picture, null, ZoneOffset.UTC)

        assertEquals("master", result.branch)
        assertEquals("drafts/2025-10-25-castle-keep", result.folder)
        assertEquals("https://github.com/mfren022/blah/commit/c1", result.commit.url)
        val files = gh.files()
        assertEquals(
            setOf("README.md", "drafts/2025-10-25-castle-keep/README.md", "drafts/2025-10-25-castle-keep/sketch.png",
                "drafts/2025-10-25-castle-keep/sketch.svg", "drafts/2025-10-25-castle-keep/draft.json"),
            files.keys,
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), files["drafts/2025-10-25-castle-keep/sketch.png"])
        val pushedDraft = DraftJson.decode(String(files.getValue("drafts/2025-10-25-castle-keep/draft.json")))
        assertEquals("The pushed draft knows its folder", result.folder, pushedDraft.remotePath)
        assertTrue(String(files.getValue("drafts/2025-10-25-castle-keep/README.md")).startsWith("# Castle keep\n\nMake the keep taller."))
        assertEquals("Add sketch: Castle keep\n\nMake the keep taller.\n\nPushed from Draftpad.", gh.messages["c1"])

        // Exactly one commit on top of the old head, made with the token.
        assertEquals("c0", gh.commits.getValue("c1").second)
        assertTrue(gh.requests.all { it.headers["Authorization"] == "Bearer secret" })
        assertTrue(gh.requests.all { it.headers["X-GitHub-Api-Version"] == "2022-11-28" })
        assertEquals(1, gh.requests.count { it.method == "PATCH" })
    }

    @Test
    fun laterPushesUpdateTheSameFolder() {
        val gh = FakeGitHub()
        val pusher = SketchPusher(GitHub("t", gh))
        val first = pusher.push(repo, "master", "drafts", draft, picture, null, ZoneOffset.UTC)
        val again = pusher.push(repo, "master", "drafts", draft.copy(remotePath = first.folder, title = "Renamed"), picture, null, ZoneOffset.UTC)
        assertEquals(first.folder, again.folder)
        assertTrue(gh.messages.getValue(again.commit.sha).startsWith("Update sketch: Renamed"))
        assertEquals(5, gh.files().size)
    }

    @Test
    fun picksAFreeFolderTheFirstTime() {
        val gh = FakeGitHub()
        gh.pushOther("master", "drafts/2025-10-25-castle-keep/README.md")
        gh.pushOther("master", "drafts/2025-10-25-castle-keep-2/README.md")
        val result = SketchPusher(GitHub("t", gh)).push(repo, "", "drafts/", draft, picture, null, ZoneOffset.UTC)
        assertEquals("drafts/2025-10-25-castle-keep-3", result.folder)
    }

    @Test
    fun includesTheBackgroundImage() {
        val gh = FakeGitHub()
        val withImage = draft.copy(background = Background(SketchPusher.BACKGROUND, 0f, 0f, 360f, 640f))
        val result = SketchPusher(GitHub("t", gh)).push(repo, null, "drafts", withImage, picture, byteArrayOf(9), ZoneOffset.UTC)
        val files = gh.files()
        assertArrayEquals(byteArrayOf(9), files["${result.folder}/background.jpg"])
        assertTrue(String(files.getValue("${result.folder}/sketch.svg")).contains("href=\"background.jpg\""))
    }

    @Test
    fun retriesWhenTheBranchMovesMeanwhile() {
        val gh = FakeGitHub()
        gh.beforeNextUpdate = { gh.pushOther("master", "other.txt") }
        val result = SketchPusher(GitHub("t", gh)).push(repo, null, "drafts", draft, picture, null, ZoneOffset.UTC)
        val files = gh.files()
        assertTrue("Keeps the other push", "other.txt" in files)
        assertTrue("${result.folder}/sketch.png" in files)
        assertEquals(2, gh.requests.count { it.method == "PATCH" })
    }

    @Test
    fun explainsFailures() {
        fun messageFor(status: Int, body: String, canPush: Boolean = true): String {
            val gh = FakeGitHub(canPush)
            if (status != 0) gh.failWith = Http.Response(status, body)
            return try {
                SketchPusher(GitHub("t", gh)).push(repo, null, "drafts", draft, picture, null, ZoneOffset.UTC)
                fail("Expected a failure")
                ""
            } catch (e: GitHubException) {
                e.message!!
            }
        }
        assertTrue(messageFor(401, "{\"message\":\"Bad credentials\"}").contains("didn't accept the token"))
        assertTrue(messageFor(403, "{\"message\":\"Resource not accessible by personal access token\"}")
            .contains("Contents: Read and write\" on it. (Resource not accessible by personal access token)"))
        assertTrue(messageFor(403, "{\"message\":\"API rate limit exceeded\"}").contains("rate limit"))
        assertTrue(messageFor(404, "{\"message\":\"Not Found\"}").startsWith("Repository mfren022/blah not found"))
        assertTrue(messageFor(0, "", canPush = false).contains("not push to it"))

        val offline = GitHub("t", { _, _, _, _ -> throw IOException("timeout") })
        try {
            offline.info(repo)
            fail()
        } catch (e: GitHubException) {
            assertTrue(e.message!!.startsWith("Couldn't reach GitHub (timeout)"))
        }
    }

    @Test
    fun checksPaths() {
        val gh = FakeGitHub()
        val github = GitHub("t", gh)
        assertTrue(github.exists(repo, "master", "README.md"))
        assertFalse(github.exists(repo, "master", "drafts"))
        assertFalse(github.exists(repo, "feature/x", "my folder/a+b"))
        assertEquals("/repos/mfren022/blah/contents/my%20folder/a%2Bb?ref=feature%2Fx", gh.requests.last().path)
    }

    @Test
    fun tokenLinkIsFilledIn() {
        val url = GitHub.newTokenUrl(repo)
        assertTrue(url.startsWith("https://github.com/settings/personal-access-tokens/new?name=Draftpad&"))
        assertTrue(url.contains("&target_name=mfren022&"))
        assertTrue(url.endsWith("&expires_in=366&contents=write"))
        assertFalse(GitHub.newTokenUrl(null).contains("target_name"))
    }
}
