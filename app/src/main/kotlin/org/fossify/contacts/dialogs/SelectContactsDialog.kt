package org.fossify.contacts.dialogs

import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.adapters.SelectContactsAdapter
import org.fossify.contacts.databinding.DialogSelectContactBinding
import org.fossify.contacts.helpers.applyDpadFocusHighlight
import org.fossify.contacts.helpers.focusItem
import org.fossify.contacts.helpers.focusedAdapterPosition
import java.util.Locale

class SelectContactsDialog(
    val activity: SimpleActivity, initialContacts: ArrayList<Contact>, val allowSelectMultiple: Boolean, val showOnlyContactsWithNumber: Boolean,
    selectContacts: ArrayList<Contact>? = null, val callback: (addedContacts: ArrayList<Contact>, removedContacts: ArrayList<Contact>) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogSelectContactBinding.inflate(activity.layoutInflater)
    private var initiallySelectedContacts = ArrayList<Contact>()

    init {
        var allContacts = initialContacts
        if (selectContacts == null) {
            val contactSources = activity.getVisibleContactSources()
            allContacts = allContacts.filter { contactSources.contains(it.source) } as ArrayList<Contact>

            if (showOnlyContactsWithNumber) {
                allContacts = allContacts.filter { it.phoneNumbers.isNotEmpty() }.toMutableList() as ArrayList<Contact>
            }

            initiallySelectedContacts = allContacts.filter { it.starred == 1 } as ArrayList<Contact>
        } else {
            initiallySelectedContacts = selectContacts
        }

        // if selecting multiple contacts is disabled, react on first contact click and dismiss the dialog
        val contactClickCallback: ((Contact) -> Unit)? = if (allowSelectMultiple) {
            null
        } else { contact ->
            callback(arrayListOf(contact), arrayListOf())
            dialog!!.dismiss()
        }

        binding.apply {
            selectContactList.adapter = SelectContactsAdapter(
                activity, allContacts, initiallySelectedContacts, allowSelectMultiple,
                selectContactList, contactClickCallback
            )

            if (root.context.areSystemAnimationsEnabled) {
                selectContactList.scheduleLayoutAnimation()
            }

            selectContactList.beVisibleIf(allContacts.isNotEmpty())
            selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())
        }

        setupFastscroller(allContacts)

        // keypad phones: Save / Cancel are the green check and red cross on the right instead of buttons at the bottom
        // of a long list (Back cancels too); picking a single contact needs no Save
        setupSideActions()
        val builder = activity.getAlertDialogBuilder()

        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
                binding.selectContactList.post { binding.selectContactList.focusItem(0) }
                alertDialog.setOnKeyListener { _, keyCode, event -> handleSideKeys(keyCode, event) }
            }
        }
    }

    private var lastListPosition = 0

    private fun setupSideActions() {
        binding.apply {
            selectContactSave.beVisibleIf(allowSelectMultiple)
            selectContactSave.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_save_green))
            selectContactCancel.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_cancel_red))
            listOf(selectContactSave, selectContactCancel).forEach { it.applyDpadFocusHighlight() }

            selectContactSave.setOnClickListener {
                dialogConfirmed()
                dialog?.dismiss()
            }
            selectContactCancel.setOnClickListener { dialog?.dismiss() }
        }
    }

    /** Right from any row jumps to Save (or Cancel), left from there back to the row. */
    private fun handleSideKeys(keyCode: Int, event: KeyEvent): Boolean {
        val list = binding.selectContactList
        val onSide = binding.selectContactSave.isFocused || binding.selectContactCancel.isFocused
        val target = when {
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && list.hasFocus() -> if (allowSelectMultiple) binding.selectContactSave else binding.selectContactCancel
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && onSide -> return true
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT && onSide -> null
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (target != null) {
                lastListPosition = list.focusedAdapterPosition().coerceAtLeast(0)
                target.requestFocus()
            } else {
                list.focusItem(lastListPosition)
            }
        }
        return true
    }

    private fun dialogConfirmed() {
        ensureBackgroundThread {
            val adapter = binding.selectContactList.adapter as? SelectContactsAdapter
            val selectedContacts = adapter?.getSelectedItemsSet()?.toList() ?: ArrayList()

            val newlySelectedContacts = selectedContacts.filter { !initiallySelectedContacts.contains(it) } as ArrayList
            val unselectedContacts = initiallySelectedContacts.filter { !selectedContacts.contains(it) } as ArrayList
            callback(newlySelectedContacts, unselectedContacts)
        }
    }

    private fun setupFastscroller(allContacts: ArrayList<Contact>) {
        // the letter bar only works by touch
        binding.letterFastscroller.beGone()
        binding.letterFastscrollerThumb.beGone()
        val adjustedPrimaryColor = activity.getProperPrimaryColor()
        binding.apply {
            letterFastscroller.textColor = root.context.getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = adjustedPrimaryColor
            letterFastscrollerThumb.fontSize = root.context.getTextSize()
            letterFastscrollerThumb.textColor = adjustedPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = adjustedPrimaryColor.getColorStateList()
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
        }

        binding.letterFastscroller.setupWithRecyclerView(binding.selectContactList, { position ->
            try {
                val name = allContacts[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.normalizeString().uppercase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }
}
