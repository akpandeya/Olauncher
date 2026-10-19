package app.olauncher.helper

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

class AndroidContactSource(context: Context) : ContactSource {
    private val resolver = context.applicationContext.contentResolver

    override fun contacts(): List<ContactEntry> {
        val out = mutableListOf<ContactEntry>()
        try {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val key = c.getString(1) ?: continue
                    val name = c.getString(2) ?: continue
                    out.add(
                        ContactEntry(
                            name,
                            ContactsContract.Contacts.getLookupUri(id, key).toString()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission revoked or provider unavailable: no rows.
        }
        return out
    }

    override fun primaryNumber(lookupUri: String): String? = try {
        val contactId = Uri.parse(lookupUri).let { uri ->
            resolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null }
        }
        if (contactId == null) null else
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    }
}
