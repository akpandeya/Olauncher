package app.olauncher.helper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.olauncher.R
import app.olauncher.data.store.SavedItemsStore
import app.olauncher.data.store.UrlExtractor

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)

        val clipText = intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: clipText
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()

        val extracted = UrlExtractor.extract(text, subject)
        if (extracted != null) {
            val (url, title) = extracted
            SavedItemsStore(this).add(url, title)
            showToast(R.string.saved_for_later)
        } else {
            showToast(R.string.no_link_found)
        }

        finish()
    }
}
