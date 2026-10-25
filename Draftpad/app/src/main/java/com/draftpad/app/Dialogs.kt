package com.draftpad.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.draftpad.core.GitHub
import com.draftpad.core.SketchPusher

object Dialogs {

    /** Asks for a label's text. [onDelete] adds a Delete button, for editing a label that exists. */
    fun text(activity: Activity, initial: String, onDone: (String) -> Unit, onDelete: (() -> Unit)? = null): AlertDialog {
        val view = activity.layoutInflater.inflate(R.layout.dialog_text, null)
        val field = view.findViewById<EditText>(R.id.text)
        field.setText(initial)
        field.setSelection(initial.length)
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.text_title)
            .setView(view)
            .setPositiveButton(if (onDelete == null) R.string.add else R.string.save) { _, _ -> onDone(field.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
        if (onDelete != null) builder.setNeutralButton(R.string.delete) { _, _ -> onDelete() }
        return showWithKeyboard(builder, field)
    }

    /** Edits a draft's title and notes. */
    fun notes(activity: Activity, title: String, notes: String, onSave: (title: String, notes: String) -> Unit): AlertDialog {
        val view = activity.layoutInflater.inflate(R.layout.dialog_notes, null)
        val titleField = view.findViewById<EditText>(R.id.title)
        val notesField = view.findViewById<EditText>(R.id.notes)
        titleField.setText(title)
        titleField.setSelection(title.length)
        notesField.setText(notes)
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.notes_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ -> onSave(titleField.text.toString().trim(), notesField.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
        return showWithKeyboard(builder, titleField)
    }

    fun confirm(activity: Activity, title: String, message: String, action: Int, onConfirm: () -> Unit): AlertDialog =
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()

    fun message(activity: Activity, title: String, message: String): AlertDialog =
        AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton(R.string.done, null).show()

    /** A spinner with [message] that can't be dismissed: the caller closes it. */
    fun progress(activity: Activity, message: String): AlertDialog {
        val pad = activity.dp(24f).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val size = activity.dp(28f).toInt()
        row.addView(ProgressBar(activity).apply { isIndeterminate = true }, LinearLayout.LayoutParams(size, size))
        row.addView(TextView(activity).apply {
            text = message
            textSize = 16f
            setTextColor(activity.getColor(R.color.ink))
            setPadding(activity.dp(16f).toInt(), 0, 0, 0)
        })
        return AlertDialog.Builder(activity).setView(row).setCancelable(false).show()
    }

    /**
     * Where pushes go: repository, token, branch and folder. "Connect" checks the token against the
     * repository before saving, then runs [onConnected].
     */
    fun github(activity: Activity, onConnected: (() -> Unit)? = null): AlertDialog {
        val app = activity.application as DraftpadApp
        val settings = app.github
        val view = activity.layoutInflater.inflate(R.layout.dialog_github, null)
        val repo = view.findViewById<EditText>(R.id.repo)
        val token = view.findViewById<EditText>(R.id.token)
        val branch = view.findViewById<EditText>(R.id.branch)
        val folder = view.findViewById<EditText>(R.id.folder)
        val status = view.findViewById<TextView>(R.id.status)
        repo.setText(settings.repoText)
        token.setText(settings.token)
        branch.setText(settings.branch)
        folder.setText(settings.folder)
        view.findViewById<View>(R.id.create_token).setOnClickListener {
            open(activity, GitHub.newTokenUrl(GitHub.Repo.parse(repo.text.toString())))
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.github_title)
            .setView(view)
            .setPositiveButton(R.string.github_connect, null)
            .setNegativeButton(R.string.cancel, null)
        if (settings.token.isNotEmpty()) {
            builder.setNeutralButton(R.string.github_forget) { _, _ ->
                settings.token = ""
                activity.toast(R.string.github_forgotten)
            }
        }
        val dialog = builder.show()

        fun say(text: CharSequence, error: Boolean) {
            status.text = text
            status.setTextColor(activity.getColor(if (error) R.color.error else R.color.ink_muted))
            status.visibility = View.VISIBLE
        }

        val connect = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        // Set here rather than in the builder, so a failed check keeps the dialog open.
        connect.setOnClickListener {
            val target = GitHub.Repo.parse(repo.text.toString())
            val key = token.text.toString().trim()
            when {
                target == null -> return@setOnClickListener say(activity.getString(R.string.github_bad_repo), true)
                key.isEmpty() -> return@setOnClickListener say(activity.getString(R.string.github_no_token), true)
            }
            say(activity.getString(R.string.github_checking), false)
            connect.isEnabled = false
            app.net.execute {
                val result = runCatching { GitHub(key, app.http).info(target!!) }
                app.main.post {
                    if (!dialog.isShowing) return@post
                    connect.isEnabled = true
                    result.onSuccess { info ->
                        if (!info.canPush) {
                            say(activity.getString(R.string.github_cant_push, info.fullName), true)
                        } else {
                            settings.repoText = info.fullName
                            settings.token = key
                            settings.branch = branch.text.toString()
                            settings.folder = folder.text.toString()
                            dialog.dismiss()
                            activity.toast(activity.getString(R.string.github_connected, info.fullName))
                            onConnected?.invoke()
                        }
                    }.onFailure { say(it.message ?: it.toString(), true) }
                }
            }
        }
        return dialog
    }

    /** Says where a pushed sketch went, with a ready-made message for Claude. */
    fun pushed(activity: Activity, result: SketchPusher.Result): AlertDialog {
        val repo = result.repo.toString()
        return AlertDialog.Builder(activity)
            .setTitle(R.string.pushed_title)
            .setMessage(activity.getString(R.string.pushed_message, result.folder, result.branch, repo))
            .setPositiveButton(R.string.copy_for_claude) { _, _ ->
                val prompt = activity.getString(R.string.claude_prompt, result.folder, result.branch, repo)
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.app_name), prompt))
                activity.toast(R.string.copied)
            }
            .setNeutralButton(R.string.view_on_github) { _, _ ->
                open(activity, "https://github.com/$repo/tree/${result.branch}/${result.folder}")
            }
            .setNegativeButton(R.string.done, null)
            .show()
    }

    fun open(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            activity.toast(R.string.no_browser)
        }
    }

    private fun showWithKeyboard(builder: AlertDialog.Builder, field: EditText): AlertDialog {
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        field.requestFocus()
        return dialog
    }
}
