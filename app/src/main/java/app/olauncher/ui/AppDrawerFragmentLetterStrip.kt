package app.olauncher.ui

import android.annotation.SuppressLint
import android.util.TypedValue
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
import app.olauncher.helper.LetterStripSizing
import app.olauncher.helper.MotionPolicy
import app.olauncher.helper.SystemMotion

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
        zone.addView(binding.letterStripHint)
        zone.addView(binding.letterPreview)
    } else {
        zone.addView(binding.letterPreview)
        zone.addView(binding.letterStripHint)
        zone.addView(binding.letterStrip)
    }
    binding.letterStripHint.setOnClickListener {
        dismissLetterStripHint()
    }
}

internal fun AppDrawerFragment.dismissLetterStripHint() {
    if (binding.letterStripHint.isVisible) {
        binding.letterStripHint.isVisible = false
        prefs.letterStripHintShown = true
    } else if (!prefs.letterStripHintShown) {
        prefs.letterStripHintShown = true
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
        binding.letterStripHint.isVisible = false
        adapter.clearHighlight()
        resetLetterStripScale()
    } else {
        val showHint = LetterStripVisibility.shouldShowHint(
            hintShown = prefs.letterStripHintShown,
            stripVisible = true,
        )
        binding.letterStripHint.isVisible = showHint
    }
}

internal fun AppDrawerFragment.populateLetterStrip(ordered: List<AppModel>) {
    if (!prefs.drawerLetterStrip) {
        binding.letterStripContainer.isVisible = false
        binding.letterStripHint.isVisible = false
        adapter.clearHighlight()
        resetLetterStripScale()
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
    binding.letterStrip.clipChildren = false
    binding.letterStripZone.clipChildren = false
    lastScaledStripIndex = -1
    adapter.clearHighlight()
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
    fitLetterStripText()

    setupLetterStripTouchListener()
    updateLetterStripVisibility()
    updateListInsetForStrip()
}

/** Shrink the letters when the strip is short (landscape) so none clip. */
private fun AppDrawerFragment.fitLetterStripText() {
    val strip = binding.letterStrip
    val apply = {
        val first = strip.getChildAt(0) as? TextView
        if (first != null && strip.height > 0) {
            val maxPx = resources.getDimension(R.dimen.text_small)
            val px = LetterStripSizing.textSizePx(strip.height, strip.childCount, maxPx)
            for (i in 0 until strip.childCount) {
                (strip.getChildAt(i) as TextView).setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            }
        }
    }
    if (strip.height > 0) apply() else strip.post { _binding?.let { apply() } }
    if (strip.getTag(R.id.letterStrip) == null) {
        strip.setTag(R.id.letterStrip, true)
        strip.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) apply()
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun AppDrawerFragment.setupLetterStripTouchListener() {
    binding.letterStrip.setOnTouchListener { view, event ->
        if (letterPosList.isEmpty()) return@setOnTouchListener false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dismissLetterStripHint()
                view.parent?.requestDisallowInterceptTouchEvent(true)
                val stripIndex = LetterIndex.letterAt(event.y, view.height, stripLetters.size)
                applyLetterStripScale(stripIndex)
                val letter = LetterIndex.nearestPresent(stripLetters, letterPosList.map { it.letter }.toSet(), stripIndex)
                val pos = letterPosList.firstOrNull { it.letter == letter }
                if (pos != null) {
                    val targetPosition = adapter.getTargetPositionForOrderedIndex(pos.index)
                    adapter.setHighlightedPosition(targetPosition)
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
                adapter.clearHighlight()
                resetLetterStripScale()
                view.parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> false
        }
    }
}

internal fun AppDrawerFragment.applyLetterStripScale(selectedIndex: Int) {
    if (selectedIndex == lastScaledStripIndex) return
    lastScaledStripIndex = selectedIndex

    val strip = binding.letterStrip
    val count = strip.childCount
    if (count == 0 || selectedIndex !in 0 until count) return

    val animatorsEnabled = context?.let {
        runCatching { SystemMotion.isAnimationsEnabled(it) }.getOrDefault(false)
    } ?: false
    val duration = MotionPolicy.durationMs(MotionPolicy.FISHEYE_DURATION_MS, animatorsEnabled)

    for (i in 0 until count) {
        val child = strip.getChildAt(i) ?: continue
        val targetScale = LetterStripSizing.scaleForDistance(i - selectedIndex)
        if (duration > 0L) {
            child.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(duration)
                .start()
        } else {
            child.animate().cancel()
            child.scaleX = targetScale
            child.scaleY = targetScale
        }
    }
}

internal fun AppDrawerFragment.resetLetterStripScale() {
    lastScaledStripIndex = -1
    val strip = binding.letterStrip
    val count = strip.childCount
    if (count == 0) return

    val animatorsEnabled = context?.let {
        runCatching { SystemMotion.isAnimationsEnabled(it) }.getOrDefault(false)
    } ?: false
    val duration = MotionPolicy.durationMs(MotionPolicy.FISHEYE_DURATION_MS, animatorsEnabled)

    for (i in 0 until count) {
        val child = strip.getChildAt(i) ?: continue
        if (duration > 0L) {
            child.animate()
                .scaleX(LetterStripSizing.SCALE_REST)
                .scaleY(LetterStripSizing.SCALE_REST)
                .setDuration(duration)
                .start()
        } else {
            child.animate().cancel()
            child.scaleX = LetterStripSizing.SCALE_REST
            child.scaleY = LetterStripSizing.SCALE_REST
        }
    }
}

internal fun AppDrawerFragment.scrollToLetter(pos: LetterPos) {
    val totalCount = adapter.itemCount
    if (totalCount <= 0) return

    val targetPosition = adapter.getTargetPositionForOrderedIndex(pos.index)
    adapter.setHighlightedPosition(targetPosition)

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
