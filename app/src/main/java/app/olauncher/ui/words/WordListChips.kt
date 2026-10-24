package app.olauncher.ui.words

/** Context-free tick logic for the list chips in the edit-word dialog. */
object WordListChips {

    /** Flips [id] in [selected] and returns whether it is ticked afterwards. */
    fun toggle(selected: MutableSet<String>, id: String): Boolean {
        if (!selected.remove(id)) selected.add(id)
        return id in selected
    }
}
