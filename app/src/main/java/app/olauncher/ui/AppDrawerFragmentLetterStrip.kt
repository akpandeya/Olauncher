package app.olauncher.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.helper.LetterStripVisibility
import app.olauncher.helper.Haptics
import app.olauncher.helper.LetterIndex
import app.olauncher.helper.LetterPos

private const val TAG = "AppDrawerFragment"

internal fun AppDrawerFragment.setupLetterStripLayout() {
    val isStart = prefs.shortcutSideStart
    val containerParams = binding.letterStripContainer.layoutParams as FrameLayout.LayoutParams
    containerParams.gravity = Gravity.BOTTOM or (if (isStart) Gravity.START else Gravity.END)
    binding.letterStripContainer.layoutParams = containerParams
    // With the search row at the bottom, keep the strip above it.
    val search = binding.searchContainer
    val clearSearch = {
        val extra = if (prefs.drawerBottomSearch && search.height > 0) {
            val lp = search.layoutParams as ViewGroup.MarginLayoutParams
            search.height + lp.bottomMargin
        } else 0
        val lp = binding.letterStripContainer.layoutParams as FrameLayout.LayoutParams
        if (lp.bottomMargin != extra) {
            lp.bottomMargin = extra
            binding.letterStripContainer.layoutParams = lp
        }
    }
    clearSearch()
    if (prefs.drawerBottomSearch) search.post { _binding?.let { clearSearch() } }

    val zone = binding.letterStripZone
    zone.removeAllViews()
    if (isStart) {
        zone.addView(binding.letterStrip)
        zone.addView(binding.letterPreview)
    } else {
        zone.addView(binding.letterPreview)
        zone.addView(binding.letterStrip)
    }
}

internal fun AppDrawerFragment.updateLetterStripVisibility(query: String? = binding.search.query?.toString()) {
    val show = LetterStripVisibility.shouldShow(
        stripEnabled = prefs.drawerLetterStrip,
        keyboardVisible = keyboardVisible,
        queryBlank = query.isNullOrBlank(),
        hasLetters = letterPosList.isNotEmpty(),
    )
    binding.letterStripContainer.isVisible = show
    if (!show) {
        binding.letterPreview.visibility = View.INVISIBLE
    }
}

internal fun AppDrawerFragment.populateLetterStrip(ordered: List<AppModel>) {
    if (!prefs.drawerLetterStrip) {
        binding.letterStripContainer.isVisible = false
        updateListInsetForStrip()
        return
    }

    // Keep positions in the full adapter list: skip headers/results but remember their real index.
    val realIndices = ordered.indices.filter {
        ordered[it] !is AppModel.PrivateSpaceHeader && ordered[it] !is AppModel.Result && ordered[it] !is AppModel.RecentHeader
    }
    letterPosList = LetterIndex.build(realIndices.map { ordered[it].appLabel })
        .map { LetterPos(it.letter, realIndices[it.index]) }

    stripLetters = LetterIndex.stripLetters(letterPosList)
    val presentLetters = letterPosList.map { it.letter }.toSet()
    binding.letterStrip.removeAllViews()
    for (letter in stripLetters) {
        val tv = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0,
                1f,
            )
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            TextViewCompat.setTextAppearance(this, R.style.TextSmall)
            text = letter.toString()
            alpha = if (letter in presentLetters) 1f else 0.3f
            isClickable = false
            isFocusable = false
        }
        binding.letterStrip.addView(tv)
    }

    setupLetterStripTouchListener()
    updateLetterStripVisibility()
    updateListInsetForStrip()
}

@SuppressLint("ClickableViewAccessibility")
internal fun AppDrawerFragment.setupLetterStripTouchListener() {
    binding.letterStrip.setOnTouchListener { view, event ->
        if (letterPosList.isEmpty()) return@setOnTouchListener false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                val stripIndex = LetterIndex.letterAt(event.y, view.height, stripLetters.size)
                val letter = LetterIndex.nearestPresent(stripLetters, letterPosList.map { it.letter }.toSet(), stripIndex)
                val pos = letterPosList.firstOrNull { it.letter == letter }
                if (pos != null) {
                    if (pos.letter != lastSelectedLetter) {
                        lastSelectedLetter = pos.letter
                        Haptics.tick(binding.root)
                        binding.letterPreview.text = pos.letter.toString()
                        binding.letterPreview.visibility = View.VISIBLE
                        scrollToLetter(pos)
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastSelectedLetter = null
                binding.letterPreview.visibility = View.INVISIBLE
                view.parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> false
        }
    }
}

internal fun AppDrawerFragment.scrollToLetter(pos: LetterPos) {
    val totalCount = adapter.itemCount
    if (totalCount <= 0) return

    val targetPosition = adapter.getTargetPositionForOrderedIndex(pos.index)

    val rv = binding.recyclerView
    val offset = LetterIndex.scrollOffsetPx(
        bottomSearch = prefs.drawerBottomSearch,
        listHeightPx = rv.height - rv.paddingTop - rv.paddingBottom,
        rowHeightPx = rv.getChildAt(0)?.height ?: 0,
    )
    linearLayoutManager.scrollToPositionWithOffset(targetPosition, offset)
}

/** Pad the list on the strip side so app names never sit under the strip. */
internal fun AppDrawerFragment.updateListInsetForStrip() {
    val rv = binding.recyclerView
    val lp = rv.layoutParams as ViewGroup.MarginLayoutParams
    val stripOn = prefs.drawerLetterStrip && !keyboardVisible && letterPosList.isNotEmpty()
    val isStart = prefs.shortcutSideStart
    val inset = if (stripOn) {
        LetterIndex.listInsetPx(
            stripWidthPx = binding.letterStrip.minimumWidth,
            rowPaddingPx = resources.getDimensionPixelSize(R.dimen.drawer_row_padding_horizontal),
            listMarginPx = if (isStart) lp.marginStart else lp.marginEnd,
        )
    } else 0
    rv.setPaddingRelative(
        if (isStart) inset else 0,
        rv.paddingTop,
        if (isStart) 0 else inset,
        rv.paddingBottom,
    )
}
