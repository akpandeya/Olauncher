package app.olauncher.ui.today.cards

import android.view.View
import androidx.fragment.app.Fragment
import app.olauncher.ui.today.TodayCards
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class TodayCardStateTest {

    private val cards: List<Any> = TodayCards.all

    private val forbidden = listOf(
        View::class.java,
        ViewBinding::class.java,
        Fragment::class.java,
        Job::class.java,
    )

    @Test
    fun cardObjectsHoldNoViewScopedState() {
        val offenders = cards.flatMap { card ->
            card.javaClass.declaredFields
                .filter { it.name != "INSTANCE" }
                .filter { field -> forbidden.any { it.isAssignableFrom(field.type) } }
                .map { "${card.javaClass.simpleName}.${it.name}: ${it.type.name}" }
        }
        assertTrue("View-scoped state on card objects: $offenders", offenders.isEmpty())
    }

    @Test
    fun instanceFieldIsTheOnlyStaticSingleton() {
        cards.forEach { card ->
            val instance = card.javaClass.declaredFields.single { it.name == "INSTANCE" }
            assertTrue(Modifier.isStatic(instance.modifiers))
        }
    }
}
