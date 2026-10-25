package com.draftpad.core

import java.time.ZoneId

/**
 * Sends a sketch to a GitHub repository as one commit in its own folder:
 *
 *     drafts/2026-10-25-castle-keep/
 *       README.md     title, notes, the picture and a summary of what is drawn
 *       sketch.png    the picture
 *       sketch.svg    the vectors, in canvas units
 *       draft.json    Draftpad's file
 *       background.jpg  the image it is drawn over, if any
 *
 * The first push picks a free folder; later pushes of the same draft update it.
 */
class SketchPusher(private val github: GitHub) {

    class Result(val repo: GitHub.Repo, val branch: String, val folder: String, val commit: GitHub.Pushed)

    /** The rendered picture of a sketch: [png] shows [frame] at [width] × [height] pixels. */
    class Picture(val png: ByteArray, val width: Int, val height: Int, val frame: Box)

    /**
     * Pushes [draft] to [branch] of [repo] (the default branch when blank), in a new folder under
     * [folder] the first time. [background] is the background image's bytes, if the draft has one.
     */
    fun push(
        repo: GitHub.Repo,
        branch: String?,
        folder: String,
        draft: Draft,
        picture: Picture,
        background: ByteArray?,
        zone: ZoneId,
        metrics: TextMetrics = TextMetrics.Estimate,
    ): Result {
        val info = github.info(repo)
        if (!info.canPush) {
            throw GitHubException("The token can read ${info.fullName} but not push to it. Give it \"Contents: Read and write\".")
        }
        val target = branch?.trim()?.takeIf { it.isNotEmpty() } ?: info.defaultBranch
        val path = draft.remotePath ?: freeFolder(repo, target, Names.remoteFolder(folder, draft, zone))
        val pushed = draft.copy(remotePath = path)
        val files = files(pushed, picture, background, metrics).map { (name, bytes) -> "$path/$name" to bytes }
        val commit = github.commit(repo, target, files, message(draft, first = draft.remotePath == null))
        return Result(repo, target, path, commit)
    }

    private fun freeFolder(repo: GitHub.Repo, branch: String, base: String): String {
        if (!github.exists(repo, branch, base)) return base
        for (n in 2..50) if (!github.exists(repo, branch, "$base-$n")) return "$base-$n"
        throw GitHubException("Couldn't find a free folder name for $base.")
    }

    companion object {
        const val README = "README.md"
        const val BACKGROUND = "background.jpg"

        /** The files of a sketch's folder, README first. */
        fun files(draft: Draft, picture: Picture, background: ByteArray?, metrics: TextMetrics): List<Pair<String, ByteArray>> {
            val bgName = draft.background?.file?.takeIf { background != null }
            val names = listOfNotNull(Readme.IMAGE, Readme.VECTOR, Readme.SOURCE, bgName)
            val readme = Readme.write(draft, picture.frame, picture.width, picture.height, names, metrics)
            return listOfNotNull(
                README to readme.toByteArray(),
                Readme.IMAGE to picture.png,
                Readme.VECTOR to Svg.write(draft, picture.frame, bgName).toByteArray(),
                Readme.SOURCE to DraftJson.encode(draft).toByteArray(),
                bgName?.let { it to background!! },
            )
        }

        fun message(draft: Draft, first: Boolean): String {
            val title = draft.title.ifBlank { "Untitled sketch" }.lineSequence().first().take(72)
            val headline = (if (first) "Add sketch: " else "Update sketch: ") + title
            val notes = draft.notes.trim()
            return if (notes.isEmpty()) "$headline\n\nPushed from Draftpad." else "$headline\n\n$notes\n\nPushed from Draftpad."
        }
    }
}
