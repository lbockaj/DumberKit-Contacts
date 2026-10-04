package org.fossify.contacts.activities

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.R
import org.fossify.contacts.adapters.ContactsAdapter
import org.fossify.contacts.databinding.ActivityGroupContactsBinding
import org.fossify.contacts.dialogs.SelectContactsDialog
import org.fossify.contacts.extensions.callContact
import org.fossify.contacts.extensions.handleGenericContactClick
import org.fossify.contacts.extensions.sendSmsToContact
import org.fossify.contacts.extensions.viewContact
import org.fossify.contacts.helpers.GROUP
import org.fossify.contacts.helpers.LOCATION_GROUP_CONTACTS
import org.fossify.contacts.helpers.makeRoundAddButton
import org.fossify.contacts.helpers.dpadCompactTopBar
import org.fossify.contacts.helpers.dpadPopupMenu
import org.fossify.contacts.helpers.focusItem
import org.fossify.contacts.helpers.focusedAdapterPosition
import org.fossify.contacts.interfaces.RefreshContactsListener
import org.fossify.contacts.interfaces.RemoveFromGroupListener

class GroupContactsActivity : SimpleActivity(), RemoveFromGroupListener, RefreshContactsListener {
    private var allContacts = ArrayList<Contact>()
    private var groupContacts = ArrayList<Contact>()
    private var wasInit = false
    private val binding by viewBinding(ActivityGroupContactsBinding::inflate)
    lateinit var group: Group

    protected val INTENT_SELECT_RINGTONE = 600

    protected var contact: Contact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateTextColors(binding.groupContactsCoordinator)

        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.groupContactsList)
        )
        setupMaterialScrollListener(binding.groupContactsList, binding.groupContactsAppbar)

        group = intent.extras?.getSerializable(GROUP) as Group
        binding.groupContactsToolbar.title = group.title

        // keypad phones: no floating button and no toolbar icons, everything is in the Menu key's popup
        binding.groupContactsFab.beGone()
        binding.groupContactsToolbar.menu.clear()
        binding.groupContactsPlaceholder2.makeRoundAddButton(getString(R.string.add_contacts))

        binding.groupContactsPlaceholder2.setOnClickListener {
            fabClicked()
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.groupContactsFastscroller.updateColors(properPrimaryColor)
    }

    override fun onResume() {
        super.onResume()
        refreshContacts()
        setupTopAppBar(binding.groupContactsAppbar, NavigationIcon.None)
        dpadCompactTopBar(binding.groupContactsAppbar, null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // open the popup on key up, otherwise the popup receives the MENU key up and stops reacting to keys
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                showMoreMenu()
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val contact = getFocusedContact()
            when (event.keyCode) {
                KeyEvent.KEYCODE_CALL -> if (contact != null) {
                    callContact(contact)
                    return true
                }

                KeyEvent.KEYCODE_POUND -> if (contact != null) {
                    sendSmsToContact(contact)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun getFocusedContact(): Contact? {
        val position = binding.groupContactsList.focusedAdapterPosition()
        return (binding.groupContactsList.adapter as? ContactsAdapter)?.contactItems?.getOrNull(position)
    }

    private fun showMoreMenu() {
        dpadPopupMenu(binding.groupContactsToolbar).apply {
            menu.add(0, R.id.dpad_group_add_contacts, 0, R.string.add_contacts)
            menu.add(0, R.id.send_sms_to_group, 1, R.string.send_sms_to_group)
            menu.add(0, R.id.send_email_to_group, 2, R.string.send_email_to_group)
            menu.add(0, R.id.assign_ringtone_to_group, 3, R.string.ringtone)
            setOnMenuItemClickListener { onMenuItemClicked(it.itemId) }
            setGravity(Gravity.END)
            show()
        }
    }

    private fun onMenuItemClicked(id: Int): Boolean {
        when (id) {
            R.id.dpad_group_add_contacts -> if (wasInit) fabClicked()
            R.id.send_sms_to_group -> sendSMSToGroup()
            R.id.send_email_to_group -> sendEmailToGroup()
            R.id.assign_ringtone_to_group -> assignRingtoneToGroup()
            else -> return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == INTENT_SELECT_RINGTONE && resultCode == Activity.RESULT_OK && resultData != null) {
            val extras = resultData.extras
            if (extras?.containsKey(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) == true) {
                val uri = extras.getParcelable<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: return
                try {
                    setRingtoneOnSelected(uri)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun fabClicked() {
        SelectContactsDialog(this, allContacts, true, false, groupContacts) { addedContacts, removedContacts ->
            ensureBackgroundThread {
                addContactsToGroup(addedContacts, group.id!!)
                removeContactsFromGroup(removedContacts, group.id!!)
                refreshContacts()
            }
        }
    }

    private fun refreshContacts() {
        ContactsHelper(this).getContacts {
            wasInit = true
            allContacts = it

            groupContacts = it.filter { it.groups.map { it.id }.contains(group.id) } as ArrayList<Contact>
            binding.groupContactsPlaceholder2.beVisibleIf(groupContacts.isEmpty())
            binding.groupContactsPlaceholder.beVisibleIf(groupContacts.isEmpty())
            binding.groupContactsFastscroller.beVisibleIf(groupContacts.isNotEmpty())
            updateContacts(groupContacts)
            focusContent()
        }
    }

    private fun sendSMSToGroup() {
        if (groupContacts.isEmpty()) {
            toast(org.fossify.commons.R.string.no_contacts_found)
        } else {
            sendSMSToContacts(groupContacts)
        }
    }

    private fun sendEmailToGroup() {
        if (groupContacts.isEmpty()) {
            toast(org.fossify.commons.R.string.no_contacts_found)
        } else {
            sendEmailToContacts(groupContacts)
        }
    }

    private fun assignRingtoneToGroup() {
        val ringtonePickerIntent = getRingtonePickerIntent()
        try {
            startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
        } catch (e: Exception) {
            toast(e.toString())
        }
    }

    private fun updateContacts(contacts: ArrayList<Contact>) {
        val currAdapter = binding.groupContactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(
                this,
                contactItems = contacts,
                recyclerView = binding.groupContactsList,
                location = LOCATION_GROUP_CONTACTS,
                removeListener = this,
                refreshListener = this,
                itemClick = {
                    contactClicked(it as Contact)
                },
                profileIconClick = {
                    viewContact(it as Contact)
                }
            ).apply {
                binding.groupContactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.groupContactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateItems(contacts)
        }
    }

    /** The first contact, or the "Add contacts" link of an empty group, unless something in the list has the focus. */
    private fun focusContent() {
        binding.groupContactsList.post {
            if (binding.groupContactsList.focusedAdapterPosition() >= 0) {
                return@post
            }
            if (groupContacts.isEmpty()) {
                binding.groupContactsPlaceholder2.requestFocus()
            } else {
                binding.groupContactsList.focusItem(0)
            }
        }
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        refreshContacts()
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    override fun removeFromGroup(contacts: ArrayList<Contact>) {
        ensureBackgroundThread {
            removeContactsFromGroup(contacts, group.id!!)
            if (groupContacts.size == contacts.size) {
                refreshContacts()
            }
        }
    }

    private fun getDefaultRingtoneUri() = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)

    private fun getRingtonePickerIntent(): Intent {
        val defaultRingtoneUri = getDefaultRingtoneUri()

        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultRingtoneUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultRingtoneUri)
        }
    }

    private fun setRingtoneOnSelected(uri: Uri) {
        groupContacts.forEach {
            ContactsHelper(this).updateRingtone(it.contactId.toString(), uri.toString())
        }
    }
}
