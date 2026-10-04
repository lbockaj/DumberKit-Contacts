package org.fossify.contacts.helpers

import android.content.Context
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.models.contacts.Contact

/**
 * Finds the duplicates of a contact (other copies with the same displayed name, e.g. the WhatsApp copy) the same way
 * as Commons' ContactsHelper.getDuplicatesOfContact(), but without loading every contact on the phone first:
 * only raw contacts linked to it or carrying the same name are checked. Must be called on a background thread.
 */
fun Context.findDuplicatesOfContact(contact: Contact): ArrayList<Contact> {
    val helper = ContactsHelper(this)
    val hash = contact.getHashToCompare()
    val candidateIds = LinkedHashSet<Int>()

    if (!contact.isPrivate()) {
        val names = ArrayList<String>()
        queryCursor(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.DISPLAY_NAME_PRIMARY, RawContacts.DISPLAY_NAME_ALTERNATIVE),
            "${RawContacts._ID} = ?",
            arrayOf(contact.id.toString())
        ) { cursor ->
            cursor.getStringValue(RawContacts.DISPLAY_NAME_PRIMARY)?.let { names.add(it) }
            cursor.getStringValue(RawContacts.DISPLAY_NAME_ALTERNATIVE)?.let { names.add(it) }
        }

        val conditions = ArrayList<String>()
        val args = ArrayList<String>()
        if (contact.contactId != 0) {
            conditions.add("${RawContacts.CONTACT_ID} = ?")
            args.add(contact.contactId.toString())
        }
        names.distinct().forEach {
            conditions.add("${RawContacts.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE")
            args.add(it)
        }

        if (conditions.isNotEmpty()) {
            val selection = "${RawContacts.DELETED} = 0 AND ${RawContacts._ID} != ? AND (${conditions.joinToString(" OR ")})"
            queryCursor(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), selection, (listOf(contact.id.toString()) + args).toTypedArray()) { cursor ->
                candidateIds.add(cursor.getIntValue(RawContacts._ID))
            }
        }
    }

    val duplicates = ArrayList<Contact>()
    candidateIds.mapNotNullTo(duplicates) { id ->
        helper.getContactWithId(id, false)?.takeIf { it.getHashToCompare() == hash }
    }

    // contacts stored only in the app ("phone storage, not visible by other apps") live in its own database
    LocalContactsHelper(this).getAllContacts()
        .filterTo(duplicates) { it.id != contact.id && it.getHashToCompare() == hash }

    return duplicates
}
