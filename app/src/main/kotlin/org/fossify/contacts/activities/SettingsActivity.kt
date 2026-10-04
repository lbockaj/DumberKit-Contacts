package org.fossify.contacts.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.View
import android.widget.Toast
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.contacts.dialogs.DpadRadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.contacts.R
import org.fossify.contacts.helpers.applyDpadFocusHighlight
import org.fossify.contacts.helpers.applyTouchInputSetting
import org.fossify.contacts.helpers.dpadCompactTopBar
import org.fossify.contacts.databinding.ActivitySettingsBinding
import org.fossify.contacts.dialogs.ExportContactsDialog
import org.fossify.contacts.dialogs.ManageAutoBackupsDialog
import org.fossify.contacts.dialogs.ManageVisibleFieldsDialog
import org.fossify.contacts.dialogs.ManageVisibleTabsDialog
import org.fossify.contacts.extensions.*
import org.fossify.contacts.helpers.VcfExporter
import java.io.OutputStream
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val PICK_IMPORT_SOURCE_INTENT = 1
        private const val PICK_EXPORT_FILE_INTENT = 2
    }

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private var ignoredExportContactSources = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.None)
        dpadCompactTopBar(binding.settingsAppbar, binding.settingsNestedScrollview)

        setupCustomizeColors()
        setupManageShownContactFields()
        setupManageShownTabs()
        setupFontSize()
        setupUseEnglish()
        setupLanguage()
        setupShowContactThumbnails()
        setupShowPhoneNumbers()
        setupEnableNumberFormatting()
        setupShowContactsWithNumbers()
        setupStartNameWithSurname()
        setupMergeDuplicateContacts()
        setupShowCallConfirmation()
        setupTouchInput()
        setupShowDialpadButton()
        setupShowPrivateContacts()
        setupOnContactClick()
        setupDefaultTab()
        setupEnableAutomaticBackups()
        setupManageAutomaticBackups()
        setupExportContacts()
        setupImportContacts()
        updateTextColors(binding.settingsHolder)

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsMainScreenLabel,
            binding.settingsListViewLabel,
            binding.settingsBackupsLabel,
            binding.settingsMigratingLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }

        setupDpadNavigation()
    }

    /** Keypad phones: clear focus highlight on every item, the first one focused on open, no dialpad button and private contacts options. */
    private fun setupDpadNavigation() {
        // the floating dialpad button doesn't exist in this app anymore
        binding.settingsShowDialpadButtonHolder.beGone()
        // private contacts are always shared with the other apps, the option is hidden (see setupShowPrivateContacts)
        binding.settingsShowPrivateContactsHolder.beGone()

        val items = ArrayList<View>()
        fun collect(view: View) {
            if (view.isClickable && view.id != View.NO_ID && resources.getResourceEntryName(view.id).endsWith("_holder")) {
                items.add(view)
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collect(view.getChildAt(i))
                }
            }
        }
        collect(binding.settingsHolder)
        items.forEach {
            it.isFocusable = true
            it.applyDpadFocusHighlight()
        }

        binding.settingsHolder.post {
            val focused = currentFocus
            if (focused == null || focused == binding.settingsNestedScrollview) {
                items.firstOrNull { it.isShown }?.requestFocusFromTouch()
            }
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            openColorCustomization()
        }
    }

    private fun setupManageShownContactFields() {
        binding.settingsManageContactFieldsHolder.setOnClickListener {
            ManageVisibleFieldsDialog(this) {}
        }
    }

    private fun setupManageShownTabs() {
        binding.settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_CONTACTS, getString(org.fossify.commons.R.string.contacts_tab)),
                RadioItem(TAB_FAVORITES, getString(org.fossify.commons.R.string.favorites_tab)),
                RadioItem(TAB_GROUPS, getString(org.fossify.commons.R.string.groups_tab)),
                RadioItem(TAB_LAST_USED, getString(org.fossify.commons.R.string.last_used_tab))
            )

            DpadRadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_CONTACTS -> org.fossify.commons.R.string.contacts_tab
            TAB_FAVORITES -> org.fossify.commons.R.string.favorites_tab
            TAB_GROUPS -> org.fossify.commons.R.string.groups_tab
            else -> org.fossify.commons.R.string.last_used_tab
        }
    )

    private fun setupFontSize() {
        binding.settingsFontSize.text = getFontSizeText()
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(org.fossify.commons.R.string.extra_large))
            )

            DpadRadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                binding.settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupShowContactThumbnails() {
        binding.settingsShowContactThumbnails.isChecked = config.showContactThumbnails
        binding.settingsShowContactThumbnailsHolder.setOnClickListener {
            binding.settingsShowContactThumbnails.toggle()
            config.showContactThumbnails = binding.settingsShowContactThumbnails.isChecked
        }
    }

    private fun setupEnableNumberFormatting(){
        binding.settingsFormatPhoneNumbers.isChecked = config.formatPhoneNumbers
        binding.settingsFormatPhoneNumbersHolder.setOnClickListener{
            binding.settingsFormatPhoneNumbers.toggle()
            config.formatPhoneNumbers = binding.settingsFormatPhoneNumbers.isChecked
        }
    }

    private fun setupShowPhoneNumbers() {
        binding.settingsShowPhoneNumbers.isChecked = config.showPhoneNumbers
        binding.settingsShowPhoneNumbersHolder.setOnClickListener {
            binding.settingsShowPhoneNumbers.toggle()
            config.showPhoneNumbers = binding.settingsShowPhoneNumbers.isChecked
        }
    }

    private fun setupShowContactsWithNumbers() {
        binding.settingsShowOnlyContactsWithNumbers.isChecked = config.showOnlyContactsWithNumbers
        binding.settingsShowOnlyContactsWithNumbersHolder.setOnClickListener {
            binding.settingsShowOnlyContactsWithNumbers.toggle()
            config.showOnlyContactsWithNumbers = binding.settingsShowOnlyContactsWithNumbers.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        binding.settingsStartNameWithSurname.isChecked = config.startNameWithSurname
        binding.settingsStartNameWithSurnameHolder.setOnClickListener {
            binding.settingsStartNameWithSurname.toggle()
            config.startNameWithSurname = binding.settingsStartNameWithSurname.isChecked
        }
    }

    private fun setupShowDialpadButton() {
        binding.settingsShowDialpadButton.isChecked = config.showDialpadButton
        binding.settingsShowDialpadButtonHolder.setOnClickListener {
            binding.settingsShowDialpadButton.toggle()
            config.showDialpadButton = binding.settingsShowDialpadButton.isChecked
        }
    }

    /** Like Commons' startCustomizationActivity, which refuses to open in apps with a package name not its own. */
    private fun openColorCustomization() {
        Intent(applicationContext, org.fossify.commons.activities.CustomizationActivity::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            startActivity(this)
        }
    }

    private fun setupShowPrivateContacts() {
        // always on, the option itself is hidden in setupDpadNavigation
        config.showPrivateContacts = true
        binding.settingsShowPrivateContacts.isChecked = config.showPrivateContacts
        binding.settingsShowPrivateContactsHolder.setOnClickListener {
            binding.settingsShowPrivateContacts.toggle()
            config.showPrivateContacts = binding.settingsShowPrivateContacts.isChecked
        }
    }

    private fun setupOnContactClick() {
        binding.settingsOnContactClick.text = getOnContactClickText()
        binding.settingsOnContactClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ON_CLICK_CALL_CONTACT, getString(org.fossify.commons.R.string.call_contact)),
                RadioItem(ON_CLICK_VIEW_CONTACT, getString(org.fossify.commons.R.string.view_contact)),
                RadioItem(ON_CLICK_EDIT_CONTACT, getString(org.fossify.commons.R.string.edit_contact))
            )

            DpadRadioGroupDialog(this@SettingsActivity, items, config.onContactClick) {
                config.onContactClick = it as Int
                binding.settingsOnContactClick.text = getOnContactClickText()
            }
        }
    }

    private fun getOnContactClickText() = getString(
        when (config.onContactClick) {
            ON_CLICK_CALL_CONTACT -> org.fossify.commons.R.string.call_contact
            ON_CLICK_VIEW_CONTACT -> org.fossify.commons.R.string.view_contact
            else -> org.fossify.commons.R.string.edit_contact
        }
    )

    private fun setupTouchInput() {
        binding.settingsTouchInput.isChecked = config.dpadTouchEnabled
        binding.settingsTouchInputHolder.setOnClickListener {
            binding.settingsTouchInput.toggle()
            config.dpadTouchEnabled = binding.settingsTouchInput.isChecked
            applyTouchInputSetting()
        }
    }

    private fun setupShowCallConfirmation() {
        binding.settingsShowCallConfirmation.isChecked = config.showCallConfirmation
        binding.settingsShowCallConfirmationHolder.setOnClickListener {
            binding.settingsShowCallConfirmation.toggle()
            config.showCallConfirmation = binding.settingsShowCallConfirmation.isChecked
        }
    }

    private fun setupMergeDuplicateContacts() {
        binding.settingsMergeDuplicateContacts.isChecked = config.mergeDuplicateContacts
        binding.settingsMergeDuplicateContactsHolder.setOnClickListener {
            binding.settingsMergeDuplicateContacts.toggle()
            config.mergeDuplicateContacts = binding.settingsMergeDuplicateContacts.isChecked
        }
    }

    private fun setupEnableAutomaticBackups() {
        binding.settingsBackupsLabel.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackupsHolder.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackups.isChecked = config.autoBackup
        binding.settingsEnableAutomaticBackupsHolder.setOnClickListener {
            val wasBackupDisabled = !config.autoBackup
            if (wasBackupDisabled) {
                ManageAutoBackupsDialog(
                    activity = this,
                    onSuccess = {
                        enableOrDisableAutomaticBackups(true)
                        scheduleNextAutomaticBackup()
                    }
                )
            } else {
                cancelScheduledAutomaticBackup()
                enableOrDisableAutomaticBackups(false)
            }
        }
    }

    private fun setupManageAutomaticBackups() {
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(isRPlus() && config.autoBackup)
        binding.settingsManageAutomaticBackupsHolder.setOnClickListener {
            ManageAutoBackupsDialog(
                activity = this,
                onSuccess = {
                    scheduleNextAutomaticBackup()
                }
            )
        }
    }

    private fun enableOrDisableAutomaticBackups(enable: Boolean) {
        config.autoBackup = enable
        binding.settingsEnableAutomaticBackups.isChecked = enable
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(enable)
    }

    private fun setupExportContacts() {
        binding.contactsExportHolder.setOnClickListener {
            tryExportContacts()
        }
    }

    private fun setupImportContacts() {
        binding.contactsImportHolder.setOnClickListener {
            tryImportContacts()
        }
    }

    private fun tryImportContacts() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/x-vcard"

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    importContacts()
                }
            }
        }
    }

    private fun importContacts() {
        FilePickerDialog(this) {
            showImportContactsDialog(it) {}
        }
    }

    private fun tryExportContacts() {
        if (isQPlus()) {
            ExportContactsDialog(this, config.lastExportPath, true) { file, ignoredContactSources ->
                ignoredExportContactSources = ignoredContactSources

                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/x-vcard"
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(org.fossify.commons.R.string.no_app_found, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    ExportContactsDialog(this, config.lastExportPath, false) { file, ignoredContactSources ->
                        getFileOutputStream(file.toFileDirItem(this), true) {
                            exportContactsTo(ignoredContactSources, it)
                        }
                    }
                }
            }
        }
    }

    private fun exportContactsTo(ignoredContactSources: HashSet<String>, outputStream: OutputStream?) {
        ContactsHelper(this).getContacts(true, false, ignoredContactSources) { contacts ->
            if (contacts.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
            } else {
                VcfExporter().exportContacts(
                    context = this,
                    outputStream = outputStream,
                    contacts = contacts,
                    showExportingToast = true
                ) { result ->
                    toast(
                        when (result) {
                            VcfExporter.ExportResult.EXPORT_OK -> org.fossify.commons.R.string.exporting_successful
                            VcfExporter.ExportResult.EXPORT_PARTIAL -> org.fossify.commons.R.string.exporting_some_entries_failed
                            else -> org.fossify.commons.R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            tryImportContactsFromFile(resultData.data!!) {}
        } else if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            try {
                val outputStream = contentResolver.openOutputStream(resultData.data!!)
                exportContactsTo(ignoredExportContactSources, outputStream)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}
