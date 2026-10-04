package org.fossify.contacts.helpers

/**
 * Set when the app changes contacts the system can't tell the contact list about (contacts stored only in the
 * app's own database, edits). The list reloads on its next resume when this or a system contacts change is pending.
 */
object ContactsChanges {
    @Volatile
    var pending = false
}
