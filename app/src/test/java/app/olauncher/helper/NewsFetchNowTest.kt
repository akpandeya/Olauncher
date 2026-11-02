package app.olauncher.helper

import app.olauncher.data.store.NewsHarvestLimits
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Context-free "Fetch now" decision (#794): plan and finished feedback. */
class NewsFetchNowTest {

    @Test
    fun `plan is Off when German is off or the News sentences switch is off`() {
        assertEquals(NewsFetchNow.Plan.Off, NewsFetchNow.plan(germanEnabled = false, newsSentencesEnabled = true, articlesLeftToday = 30))
        assertEquals(NewsFetchNow.Plan.Off, NewsFetchNow.plan(germanEnabled = true, newsSentencesEnabled = false, articlesLeftToday = 30))
        assertEquals(NewsFetchNow.Plan.Off, NewsFetchNow.plan(germanEnabled = false, newsSentencesEnabled = false, articlesLeftToday = 30))
    }

    @Test
    fun `plan is CapUsed when the daily article budget is spent`() {
        assertEquals(NewsFetchNow.Plan.CapUsed, NewsFetchNow.plan(germanEnabled = true, newsSentencesEnabled = true, articlesLeftToday = 0))
        assertEquals(NewsFetchNow.Plan.CapUsed, NewsFetchNow.plan(germanEnabled = true, newsSentencesEnabled = true, articlesLeftToday = -1))
    }

    @Test
    fun `plan runs with the smaller of the day's budget and the per-run cap`() {
        assertEquals(NewsFetchNow.Plan.Run(10), NewsFetchNow.plan(germanEnabled = true, newsSentencesEnabled = true, articlesLeftToday = 30))
        assertEquals(NewsFetchNow.Plan.Run(3), NewsFetchNow.plan(germanEnabled = true, newsSentencesEnabled = true, articlesLeftToday = 3))
        assertEquals(
            NewsFetchNow.Plan.Run(2),
            NewsFetchNow.plan(germanEnabled = true, newsSentencesEnabled = true, articlesLeftToday = 2, maxArticlesPerRun = 5),
        )
    }

    @Test
    fun `finished reports the articles fetched, or nothing new when there were none`() {
        assertEquals(NewsFetchNow.Finished.Fetched(7), NewsFetchNow.finished(articlesFetched = 7))
        assertEquals(NewsFetchNow.Finished.Fetched(1), NewsFetchNow.finished(articlesFetched = 1))
        assertEquals(NewsFetchNow.Finished.NothingNew, NewsFetchNow.finished(articlesFetched = 0))
        // A negative delta (another run already counted the same articles) is not a fetch.
        assertEquals(NewsFetchNow.Finished.NothingNew, NewsFetchNow.finished(articlesFetched = -2))
    }

    @Test
    fun `plan keeps the NewsHarvestLimits per-run default`() {
        assertEquals(
            NewsHarvestLimits.DEFAULT_MAX_ARTICLES_PER_RUN,
            NewsHarvestLimits(articlesLeftToday = 0).maxArticlesPerRun,
        )
    }
}
