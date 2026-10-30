package app.olauncher.data.store

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A write that could not be persisted. [cause] names the failure (file system
 * problem only — never API keys or message content).
 */
class AiChatStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Outcome of one mutating call on [AiChatStore]. Storage failure is surfaced, not swallowed. */
sealed class AiChatWrite {
    object Ok : AiChatWrite()
    data class Failed(val cause: AiChatStoreException) : AiChatWrite()
}

/**
 * Outcome of loading the store. [Recovered] means the file on disk was corrupt:
 * it was renamed to `ai_chats.json.bak` and the store starts empty, so the UI can
 * tell the learner once that their conversations could not be loaded (design
 * §Reliability: "A corrupt store is renamed `ai_chats.json.bak` and chat starts
 * fresh"). [Unreadable] means the file exists but could not be read (I/O): it is
 * kept untouched and mutations refuse to overwrite it.
 */
sealed class AiChatLoad {
    data class Ok(val threads: List<AiChatThread>) : AiChatLoad()
    data class Recovered(val reason: String) : AiChatLoad()
    data class Unreadable(val cause: AiChatStoreException) : AiChatLoad()
}

/**
 * Durable store of Ask-AI chat threads in `filesDir/ai_chats.json`
 * (docs/design/ask-ai-chat.md §Data), with the same codec and atomic-write
 * pattern as [WordsStore]: write to a `.tmp` file, then rename with a fallback
 * chain. The store never reads or writes any other file, and it is not part of
 * [Backup] (chat history stays on-device, out of scope for export).
 */
class AiChatStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, FILE_NAME)

    /**
     * A recovery the store has already performed but [load] has not reported
     * yet, so a recovery triggered by a mutation is still surfaced to the UI
     * once. Guarded by [LOCK]: every access happens inside a synchronized block.
     */
    private var pendingRecovery: String? = null

    /** Loads with recovery reporting; public so the UI can tell the learner once. */
    fun load(): AiChatLoad = synchronized(LOCK) {
        val pending = pendingRecovery
        if (pending != null) {
            // A mutation already recovered; this load is the one read that reports it.
            pendingRecovery = null
            return@synchronized AiChatLoad.Recovered(pending)
        }
        when (val result = loadLocked()) {
            is DiskLoad.Ok -> AiChatLoad.Ok(result.threads)
            is DiskLoad.Recovered -> {
                // loadLocked recorded the recovery; this call reports it, once.
                pendingRecovery = null
                AiChatLoad.Recovered(result.reason)
            }
            is DiskLoad.Unreadable -> AiChatLoad.Unreadable(result.cause)
        }
    }

    fun threads(): List<AiChatThread> = synchronized(LOCK) {
        when (val result = loadLocked()) {
            is DiskLoad.Ok -> result.threads
            // Recovered starts empty; unreadable reads must not throw.
            is DiskLoad.Recovered, is DiskLoad.Unreadable -> emptyList()
        }
    }

    fun thread(id: String): AiChatThread? = synchronized(LOCK) {
        threads().firstOrNull { it.id == id }
    }

    fun threadsFor(subject: AiChatSubject): List<AiChatThread> = synchronized(LOCK) {
        threads().filter { it.subject == subject }
    }

    /** Replaces the thread with [thread]'s id or appends it; exactly one write. */
    fun upsert(thread: AiChatThread): AiChatWrite = synchronized(LOCK) {
        when (val base = mutationBase()) {
            is MutationBase.Refused -> AiChatWrite.Failed(base.cause)
            is MutationBase.Ready -> {
                val updated = base.threads.filterNot { it.id == thread.id } + thread
                save(updated)
            }
        }
    }

    /** Removes the thread with [threadId]; a no-op on an unknown id is still [AiChatWrite.Ok]. */
    fun delete(threadId: String): AiChatWrite = synchronized(LOCK) {
        when (val base = mutationBase()) {
            is MutationBase.Refused -> AiChatWrite.Failed(base.cause)
            is MutationBase.Ready ->
                if (base.threads.none { it.id == threadId }) {
                    AiChatWrite.Ok
                } else {
                    save(base.threads.filterNot { it.id == threadId })
                }
        }
    }

    /**
     * The threads a mutation starts from, or a refusal: the file is unreadable,
     * or corrupt and it could not be moved to `.bak`, so a write now might
     * destroy recoverable data. A successful recovery starts from an empty
     * store; its reason is remembered in [pendingRecovery] by [loadLocked].
     */
    private fun mutationBase(): MutationBase = when (val result = loadLocked()) {
        is DiskLoad.Ok -> MutationBase.Ready(result.threads)
        is DiskLoad.Recovered ->
            if (result.renamedToBackup) {
                MutationBase.Ready(emptyList())
            } else {
                MutationBase.Refused(
                    AiChatStoreException(
                        "corrupt $FILE_NAME could not be moved to $BACKUP_NAME; refusing to overwrite"
                    )
                )
            }
        is DiskLoad.Unreadable -> MutationBase.Refused(result.cause)
    }

    private fun loadLocked(): DiskLoad {
        if (!file.exists()) return DiskLoad.Ok(emptyList())
        val raw = try {
            file.readText()
        } catch (e: Exception) {
            // Unreadable (I/O), not provably corrupt: keep the file untouched.
            // Renaming it to .bak or overwriting it could destroy recoverable
            // data, so reads report the failure and mutations refuse to write.
            return DiskLoad.Unreadable(AiChatStoreException("Could not read $FILE_NAME", e))
        }
        return when (val decoded = AiChatCodec.decode(raw)) {
            is AiChatCodec.AiChatDecoded.Decoded -> DiskLoad.Ok(decoded.threads)
            is AiChatCodec.AiChatDecoded.Corrupt -> {
                val backup = File(filesDir, BACKUP_NAME)
                if (backup.exists()) backup.delete()
                val renamed = try {
                    file.renameTo(backup)
                } catch (e: Exception) {
                    false
                }
                val reason = if (renamed) {
                    "corrupt store: ${decoded.reason}"
                } else {
                    "corrupt store: ${decoded.reason} (backup rename failed)"
                }
                // Remember the recovery so [load] reports it once even when a
                // mutation or read was the call that moved the file aside.
                pendingRecovery = reason
                DiskLoad.Recovered(reason, renamed)
            }
        }
    }

    /**
     * Writes the threads atomically (`.tmp` then rename with fallbacks).
     * `internal` as a test seam (#708 follow-up): the failure path where the
     * rename target is blocked cannot be reached through [upsert]/[delete] —
     * [mutationBase] refuses the mutation first — so AiChatStoreTest calls
     * this directly with a target that cannot be renamed onto and asserts the
     * `.tmp` file is cleaned up.
     */
    internal fun save(threads: List<AiChatThread>): AiChatWrite {
        val tempFile = File(filesDir, "$FILE_NAME.tmp")
        return try {
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }
            tempFile.writeText(AiChatCodec.encode(threads))
            if (!tempFile.renameTo(file)) {
                try {
                    Files.move(
                        tempFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (e: Exception) {
                    if (file.exists()) file.delete()
                    if (!tempFile.renameTo(file)) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                }
            }
            AiChatWrite.Ok
        } catch (e: Exception) {
            // A failed write must not leave the temp file behind.
            tempFile.delete()
            AiChatWrite.Failed(AiChatStoreException("Could not write $FILE_NAME", e))
        }
    }

    /** Result of reading the store file under [LOCK]. */
    private sealed class DiskLoad {
        data class Ok(val threads: List<AiChatThread>) : DiskLoad()

        /** The file was corrupt; [renamedToBackup] is false when the `.bak` move failed. */
        data class Recovered(val reason: String, val renamedToBackup: Boolean) : DiskLoad()
        data class Unreadable(val cause: AiChatStoreException) : DiskLoad()
    }

    /** What a mutation starts from: the current threads, or why it must not write. */
    private sealed class MutationBase {
        data class Ready(val threads: List<AiChatThread>) : MutationBase()
        data class Refused(val cause: AiChatStoreException) : MutationBase()
    }

    companion object {
        const val FILE_NAME = "ai_chats.json"
        const val BACKUP_NAME = "$FILE_NAME.bak"

        /**
         * One lock for every instance: multiple screens and the chat engine each
         * build their own store, and the file's read-modify-write must never
         * interleave — a lost upsert deletes turns the learner wrote.
         */
        private val LOCK = Any()
    }
}
