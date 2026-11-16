package com.hippo.ehviewer.tags

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.hippo.ehviewer.R

/** Edits a persistent draft; applying and resetting are explicit, separate actions. */
class LocalTagFilterDialog(private val context: Context, private val apply: (String?, Boolean) -> Unit) {
    fun show(): AlertDialog {
        val preferences = context.getSharedPreferences("local_tag_filter", Context.MODE_PRIVATE)
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_local_tag_filter, null)
        val input = content.findViewById<EditText>(R.id.tag_filter_input)
        (input as TagAutoCompleteTextView).setAdapter(TagSuggestionAdapter(context))
        input.setText(preferences.getString("draft", ""))
        input.setSelection(input.length())
        input.doAfterTextChanged { text ->
            preferences.edit().putString("draft", text.toString()).apply()
            input.error = null
        }
        content.findViewById<android.view.View>(R.id.tag_filter_example).setOnClickListener {
            input.setText(EXAMPLE)
            input.setSelection(input.length())
            input.requestFocus()
        }
        val dialog = AlertDialog.Builder(context).setTitle(R.string.tag_filter).setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.tag_filter_reset, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        content.findViewById<android.view.View>(R.id.tag_filter_unknown).setOnClickListener {
            apply(null, true)
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString().trim()
                try {
                    TagQuery.parse(query)
                    apply(query.takeIf { it.isNotEmpty() }, false)
                    dialog.dismiss()
                } catch (error: IllegalArgumentException) {
                    input.error = error.message
                    input.requestFocus()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                input.setText("")
                preferences.edit().remove("draft").apply()
                apply(null, false)
            }
        }
        dialog.show()
        return dialog
    }

    companion object {
        const val EXAMPLE = "o:\"extraneous ads$\""
    }
}
