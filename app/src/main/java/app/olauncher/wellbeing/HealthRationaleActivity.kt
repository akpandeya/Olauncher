package app.olauncher.wellbeing

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R

/** Privacy rationale shown by Health Connect. */
class HealthRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(TextView(this).apply {
            setText(R.string.health_connect_rationale)
            setPadding(pad, pad, pad, pad)
        })
    }
}
