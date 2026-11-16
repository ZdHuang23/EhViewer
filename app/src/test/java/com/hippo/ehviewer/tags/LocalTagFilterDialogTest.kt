package com.hippo.ehviewer.tags

import android.content.Context
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.hippo.ehviewer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = android.app.Application::class)
class LocalTagFilterDialogTest {
    private fun context(): Context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), androidx.appcompat.R.style.Theme_AppCompat)

    @Test fun exampleBecomesEditableTextAndCancelPreservesDraft() {
        val context = context()
        context.getSharedPreferences("local_tag_filter", Context.MODE_PRIVATE).edit().clear().commit()
        var applied = false
        val first = LocalTagFilterDialog(context) { _, _ -> applied = true }.show().also { shadowOf(Looper.getMainLooper()).idle() }
        assertEquals("", first.findViewById<EditText>(R.id.tag_filter_input)!!.text.toString())
        first.findViewById<android.view.View>(R.id.tag_filter_example)!!.performClick()
        assertEquals(LocalTagFilterDialog.EXAMPLE, first.findViewById<EditText>(R.id.tag_filter_input)!!.text.toString())
        first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertFalse(applied)
        val second = LocalTagFilterDialog(context) { _, _ -> }.show().also { shadowOf(Looper.getMainLooper()).idle() }
        assertEquals(LocalTagFilterDialog.EXAMPLE, second.findViewById<EditText>(R.id.tag_filter_input)!!.text.toString())
        second.dismiss()
    }

    @Test fun resetClearsDraftAndAppliedFilter() {
        val context = context()
        context.getSharedPreferences("local_tag_filter", Context.MODE_PRIVATE).edit().putString("draft", "l:chinese$").commit()
        var reset = false
        val dialog = LocalTagFilterDialog(context) { query, unknown -> reset = query == null && !unknown }.show().also { shadowOf(Looper.getMainLooper()).idle() }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        assertTrue(reset)
        assertEquals("", dialog.findViewById<EditText>(R.id.tag_filter_input)!!.text.toString())
        dialog.dismiss()
        val reopened = LocalTagFilterDialog(context) { _, _ -> }.show().also { shadowOf(Looper.getMainLooper()).idle() }
        assertEquals("", reopened.findViewById<EditText>(R.id.tag_filter_input)!!.text.toString())
        reopened.dismiss()
    }

    @Test fun invalidQueryStaysOpenWithError() {
        val dialog = LocalTagFilterDialog(context()) { _, _ -> throw AssertionError("Must not apply invalid query") }.show().also { shadowOf(Looper.getMainLooper()).idle() }
        val input = dialog.findViewById<EditText>(R.id.tag_filter_input)!!
        input.setText("l:chinese$;l:japanese$")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertTrue(input.error != null)
        dialog.dismiss()
    }
}
