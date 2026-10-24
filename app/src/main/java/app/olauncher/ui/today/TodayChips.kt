package app.olauncher.ui.today

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import app.olauncher.R

object TodayChips {

    fun newSelectableChip(context: Context, label: CharSequence): TextView = TextView(context).apply {
        TextViewCompat.setTextAppearance(this, R.style.TodayChip)
        text = label
        background = ContextCompat.getDrawable(context, R.drawable.today_chip_selectable)
        minHeight = context.resources.getDimensionPixelSize(R.dimen.today_touch_min_height)
        val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.today_chip_padding_horizontal)
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
    }
}
