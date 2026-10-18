package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.Capture
import app.olauncher.data.store.WordCapture
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.ActivityWordCaptureBinding

class WordCaptureActivity : AppCompatActivity() {

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, WordCaptureActivity::class.java)
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)
        super.onCreate(savedInstanceState)

        val rawText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent?.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""

        when (val capture = WordCapture.classify(rawText)) {
            is Capture.Word -> {
                saveWord(capture.text, context = "")
                finish()
            }
            is Capture.Sentence -> {
                showSentencePicker(capture, prefs)
            }
            Capture.Invalid -> {
                showToast(R.string.select_word_or_sentence)
                finish()
            }
        }
    }

    private fun saveWord(text: String, context: String) {
        val word = WordsStore(this).add(text, context)
        WordLookup.enqueue(this, word.id)
        showToast(getString(R.string.saved_word_de, text))
    }

    private fun showSentencePicker(capture: Capture.Sentence, prefs: Prefs) {
        val binding = ActivityWordCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.captureScrim.setOnClickListener {
            finish()
        }

        binding.captureSheet.setOnClickListener {
            // Prevent clicks inside the sheet from dismissing via scrim
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        val typeface = Typeface.create(if (prefs.boldFont) "sans-serif" else "sans-serif-light", Typeface.NORMAL)
        val textColor = getColorFromAttr(R.attr.primaryColor)
        val paddingHorizontal = (10 * resources.displayMetrics.density).toInt()
        val paddingVertical = (8 * resources.displayMetrics.density).toInt()

        val outValue = TypedValue()
        val selectableBackground = if (theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            outValue.resourceId
        } else 0

        for (token in capture.tokens) {
            val textView = TextView(this).apply {
                text = token
                setTextAppearance(R.style.TextLarge)
                setTextColor(textColor)
                this.typeface = typeface
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                if (selectableBackground != 0) {
                    setBackgroundResource(selectableBackground)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    saveWord(token, context = capture.text)
                    finish()
                }
            }
            binding.wordsContainer.addView(textView)
        }
    }
}
