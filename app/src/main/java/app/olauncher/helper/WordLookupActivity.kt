package app.olauncher.helper

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.data.store.WordEdit
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.WordLookupShare
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.ActivityWordLookupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Share target: look up a German word quickly, then optionally save it. */
class WordLookupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordLookupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)
        super.onCreate(savedInstanceState)

        val word = WordLookupShare.parse(readIncomingText())
        if (word == null) {
            showToast(R.string.not_a_word_to_look_up)
            finish()
            return
        }

        binding = ActivityWordLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.lookupScrim.setOnClickListener { finish() }
        binding.lookupSheet.setOnClickListener { }
        onBackPressedDispatcher.addCallback(this) { finish() }
        val sheetBottomPad = binding.lookupSheet.paddingBottom
        binding.lookupSheet.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sheetBottomPad + insets.systemWindowInsetBottom)
            insets
        }

        val typeface = Typeface.create(if (prefs.boldFont) "sans-serif" else "sans-serif-light", Typeface.NORMAL)
        listOf(binding.lookupWord, binding.lookupMeaning, binding.lookupOpen, binding.lookupSave)
            .forEach { it.typeface = typeface }
        binding.lookupWord.text = word
        binding.lookupOpen.setOnClickListener { openUrl(WordEdit.wiktionaryUrl(word)) }

        val saved = WordsStore(this).all().firstOrNull { it.text.equals(word, ignoreCase = true) }
        var foundMeaning: String? = null
        binding.lookupSave.setOnClickListener { saveWord(word, foundMeaning) }

        if (saved != null) {
            showMeaning(saved.meaning.ifBlank { getString(R.string.word_row_no_meaning_yet) })
            markSaved()
            return
        }
        if (!WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)) return

        showMeaning(getString(R.string.word_row_looking_up))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { WiktionaryApi.lookup(word) }
            when (result) {
                is WiktionaryApi.LookupResult.Found -> {
                    foundMeaning = result.meaning
                    showMeaning(getString(R.string.word_row_wiktionary_meaning, word, result.meaning))
                }
                WiktionaryApi.LookupResult.NotFound -> showMeaning(getString(R.string.word_row_no_meaning_found))
                WiktionaryApi.LookupResult.Failed -> showMeaning(getString(R.string.word_row_unreachable))
            }
        }
    }

    private fun showMeaning(text: String) {
        binding.lookupMeaning.text = text
        binding.lookupMeaning.visibility = View.VISIBLE
    }

    private fun markSaved() {
        binding.lookupSave.text = getString(R.string.word_lookup_saved)
        binding.lookupSave.isEnabled = false
        binding.lookupSave.alpha = 0.5f
    }

    private fun saveWord(text: String, meaning: String?) {
        val store = WordsStore(this)
        val word = store.add(text)
        if (!meaning.isNullOrBlank() && word.meaning.isBlank()) {
            store.update(word.copy(meaning = meaning, meaningSource = "wiktionary"))
        } else {
            WordLookup.enqueue(this, word.id)
        }
        markSaved()
    }

    private fun readIncomingText(): String {
        val i = intent ?: return ""
        if (i.action != Intent.ACTION_SEND) return ""
        val clipText = i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        return i.getStringExtra(Intent.EXTRA_TEXT)
            ?: i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: clipText
            ?: ""
    }
}
