package app.olauncher.data.store

/** One verb entry row from the offline dictionary: its id and lemma. */
data class VerbEntryRef(val id: Long, val lemma: String)

object ParadigmEntries {

    /**
     * Keeps only the entries sharing the first entry's lemma (the query orders an
     * exact lemma match first). Same-lemma homographs (übersetzen separable and
     * inseparable) still merge; different verbs that merely share an inflected
     * spelling are dropped so their forms never mix into one drill.
     */
    fun sameLemmaAsFirst(entries: List<VerbEntryRef>): List<VerbEntryRef> {
        val lemma = entries.firstOrNull()?.lemma ?: return emptyList()
        return entries.filter { it.lemma == lemma }
    }
}
