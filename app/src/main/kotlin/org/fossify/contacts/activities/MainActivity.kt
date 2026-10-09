package org.fossify.contacts.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.database.ContentObserver
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.os.Looper
import android.os.Handler
import android.os.LocaleList
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager.widget.ViewPager
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.grantland.widget.AutofitHelper
import org.fossify.commons.databases.ContactsDatabase
import org.fossify.commons.databinding.BottomTablayoutItemBinding
import org.fossify.commons.dialogs.ChangeViewTypeDialog
import org.fossify.contacts.dialogs.DpadRadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.helpers.dpadPopupMenu
import org.fossify.contacts.adapters.ViewPagerAdapter
import org.fossify.contacts.databinding.ActivityMainBinding
import org.fossify.contacts.dialogs.ChangeSortingDialog
import org.fossify.contacts.dialogs.FilterContactSourcesDialog
import org.fossify.contacts.dialogs.DPAD_OTHER_LETTER
import org.fossify.contacts.dialogs.DpadLetterDialog
import org.fossify.contacts.extensions.callContact
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.handleGenericContactClick
import org.fossify.contacts.extensions.sendSmsToContact
import org.fossify.contacts.extensions.tryImportContactsFromFile
import org.fossify.contacts.fragments.FavoritesFragment
import org.fossify.contacts.fragments.GroupsFragment
import org.fossify.contacts.fragments.MyViewPagerFragment
import org.fossify.contacts.helpers.ALL_TABS_MASK
import org.fossify.contacts.helpers.ContactsChanges
import org.fossify.contacts.helpers.applyDpadFocusHighlight
import org.fossify.contacts.helpers.focusItem
import org.fossify.contacts.helpers.focusedAdapterPosition
import org.fossify.contacts.helpers.tabsList
import org.fossify.contacts.interfaces.RefreshContactsListener
import java.util.Arrays
import java.util.Locale

class MainActivity : SimpleActivity(), RefreshContactsListener {
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false
    private var focusTopBarOnStart = true

    /** Title of a group just created on the Groups tab, focused once the list is reloaded ("" = just the list). */
    var focusGroupAfterRefresh: String? = null

    /**
     * After creating or deleting a group: the group list is loaded on its own, a bit after the contacts, so the focus
     * goes to the new group (or back into the list / onto "Create group") only once it's there, not to the top bar.
     */
    fun focusPendingGroup(groups: List<Group>) {
        val title = focusGroupAfterRefresh ?: return
        focusGroupAfterRefresh = null
        val fragment = findViewById<GroupsFragment>(R.id.groups_fragment) ?: return
        fragment.post {
            val position = groups.indexOfFirst { it.title == title }
            fragment.focusContent(position)
        }
    }
    private var pausedListPosition = -1
    private var allowLeavingList = false
    private var contactsChanged = false
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            contactsChanged = true
        }
    }

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowTabs = 0

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private val isSearchOpen get() = binding.mainSearchBar.visibility == View.VISIBLE
    private val toolbarButtons get() = listOf(binding.mainLetters, binding.mainSearch, binding.mainMore)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupToolbar()
        keepFocusInList()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.mainTopBar),
            padBottomImeAndSystem = listOf(binding.mainTabsHolder),
        )
        storeStateVariables()
        setupTabs()
        checkContactPermissions()
        checkWhatsNewDialog()
    }

    private var isContactsObserverRegistered = false

    /** Only with the contacts permission: watching the contacts provider without it throws (fresh install). */
    private fun registerContactsObserver() {
        if (isContactsObserverRegistered) {
            return
        }

        try {
            contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, contactsObserver)
            isContactsObserverRegistered = true
        } catch (e: SecurityException) {
        }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            werePermissionsHandled = true
            if (it) {
                registerContactsObserver()
                handlePermission(PERMISSION_WRITE_CONTACTS) {
                    handlePermission(PERMISSION_GET_ACCOUNTS) {
                        initFragments()
                    }
                }
            } else {
                initFragments()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        val configShowContactThumbnails = config.showContactThumbnails
        if (storedShowContactThumbnails != configShowContactThumbnails) {
            getAllFragments().forEach {
                it?.showContactThumbnailsChanged(configShowContactThumbnails)
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.mainTabsHolder.background = ColorDrawable(getProperBackgroundColor())
        binding.mainTabsHolder.setSelectedTabIndicatorColor(properPrimaryColor)
        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), properPrimaryColor)
        }

        updateToolbarColors()
        setupTabColors()

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
            findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        if (werePermissionsHandled && !isFirstResume) {
            if (binding.viewPager.adapter == null) {
                initFragments()
            } else if (contactsChanged || ContactsChanges.pending) {
                // coming back from a contact etc. reloads the list only if something changed meanwhile
                refreshContacts(ALL_TABS_MASK)
            }
        }

        isFirstResume = false
        checkShortcuts()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
        pausedListPosition = getCurrentFragment()?.getListView()?.focusedAdapterPosition() ?: -1
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isContactsObserverRegistered) {
            contentResolver.unregisterContentObserver(contactsObserver)
        }
        if (!isChangingConfigurations) {
            ContactsDatabase.destroyInstance()
        }
    }

    override fun onBackPressedCompat(): Boolean {
        if (isSearchOpen) {
            closeSearch()
            return true
        }

        // anywhere else than on the first contact, Back first jumps there, only then it leaves the app
        val list = getCurrentFragment()?.getListView()
        if (list != null && list.focusedAdapterPosition() != 0 && (list.adapter?.itemCount ?: 0) > 0) {
            focusFirstContact(list)
            return true
        }
        return false
    }

    private fun focusFirstContact(list: RecyclerView) = focusContactAt(list, 0)

    /** Focuses the contact at [position], scrolled to the top of the list unless it's fully on screen already. */
    private fun focusContactAt(list: RecyclerView, position: Int) {
        val holder = list.findViewHolderForAdapterPosition(position)?.itemView
        val fullyShown = holder != null && holder.top >= 0 && holder.bottom <= list.height
        // laid out, or the first one just above the screen: focusing it scrolls it fully into view
        if (holder != null && (fullyShown || position == 0)) {
            holder.requestFocus()
            return
        }

        // a jump recycles the focused row, the focus would land on the top bar ("+" flashes) and keepFocusInList
        // would pull it back to the old row; the list itself holds the focus meanwhile
        val wasFocusable = list.isFocusable
        list.isFocusable = true
        list.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        list.requestFocus()
        list.doOnNextLayout {
            list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            list.isFocusable = wasFocusable
        }
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0) ?: list.scrollToPosition(position)
    }

    /** The "Ab" button: pick a letter, the list jumps to the first contact starting with it. */
    private fun showLetterDialog() {
        val list = getCurrentFragment()?.getListView() ?: return
        val contacts = (list.adapter as? org.fossify.contacts.adapters.ContactsAdapter)?.contactItems ?: return
        val firstPositions = LinkedHashMap<String, Int>()
        contacts.forEachIndexed { index, contact -> firstPositions.putIfAbsent(letterOf(contact), index) }
        if (firstPositions.isEmpty()) {
            return
        }

        DpadLetterDialog(this, firstPositions.keys, getFocusedContact()?.let { letterOf(it) }) { letter ->
            firstPositions[letter]?.let { focusContactAt(list, it) }
        }
    }

    /** Same first letter as the list's fast scroller (accents removed), anything else than A–Z under "#". */
    private fun letterOf(contact: Contact): String {
        val letter = contact.getFirstLetter()
        return if (letter.length == 1 && letter[0] in 'A'..'Z') letter else DPAD_OTHER_LETTER
    }

    /**
     * The neighbouring tabs stay laid out next to the visible one, so up/down could move the focus into their lists,
     * where it can't be seen; only the visible tab takes the focus.
     */
    private fun updateTabFocusability() {
        val current = getCurrentFragment()
        getAllFragments().forEach {
            it?.descendantFocusability = if (it == current) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    /** Groups have no contacts to jump between. */
    private fun updateLettersButton() {
        binding.mainLetters.beVisibleIf(getCurrentFragment() !is GroupsFragment)
    }

    private fun refreshMenuItems(menu: Menu) {
        val currentFragment = getCurrentFragment()
        menu.apply {
            findItem(R.id.create_new).setTitle(
                when (currentFragment) {
                    is GroupsFragment -> R.string.create_new_group
                    is FavoritesFragment -> org.fossify.commons.R.string.add_favorites
                    else -> org.fossify.commons.R.string.create_new_contact
                }
            )
            findItem(R.id.sort).isVisible = currentFragment != findViewById(R.id.groups_fragment)
            findItem(R.id.filter).isVisible = currentFragment != findViewById(R.id.groups_fragment)
            findItem(R.id.dialpad).isVisible = true
            findItem(R.id.change_view_type).isVisible = currentFragment == findViewById(R.id.favorites_fragment)
            findItem(R.id.column_count).isVisible = currentFragment == findViewById(R.id.favorites_fragment) && config.viewType == VIEW_TYPE_GRID
            findItem(R.id.more_apps_from_us).isVisible = false
        }
    }

    private fun setupToolbar() {
        toolbarButtons.forEach { it.applyDpadFocusHighlight() }
        binding.mainSearchClose.applyDpadFocusHighlight()

        binding.mainSearch.setOnClickListener { openSearch() }
        binding.mainLetters.setOnClickListener { showLetterDialog() }
        binding.mainMore.setOnClickListener { showMoreMenu() }
        binding.mainSearchClose.setOnClickListener { closeSearch() }

        // Traditional T9 and similar keyboards: "visible password" disables predictive mode, so names are typed
        // letter by letter (ABC), and the locale hint switches to English just for this field
        binding.mainSearchInput.imeHintLocales = LocaleList(Locale.ENGLISH)

        binding.mainSearchInput.doAfterTextChanged { text ->
            if (isSearchOpen) {
                getCurrentFragment()?.onSearchQueryChanged(text?.toString() ?: "")
            }
        }
    }

    /**
     * Commons' hideKeyboard() clears the focus before opening a contact, calling etc., which throws the D-pad focus
     * to the toolbar. Put it back on the contact unless we moved it away on purpose.
     */
    private fun keepFocusInList() {
        var lastListPosition = -1
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            val list = getCurrentFragment()?.getListView() ?: return@addOnGlobalFocusChangeListener
            val item = newFocus?.let { list.findContainingItemView(it) }
            when {
                item != null -> lastListPosition = list.getChildAdapterPosition(item)
                allowLeavingList || newFocus == null || newFocus.isInside(binding.viewPager) -> lastListPosition = -1
                lastListPosition >= 0 -> {
                    // move it back right away, before the next frame, so the toolbar doesn't flash as focused
                    val position = lastListPosition
                    val item = list.findViewHolderForAdapterPosition(position)?.itemView
                    if (item != null) {
                        item.requestFocus()
                    } else {
                        list.post { list.focusItem(position) }
                    }
                }
            }
        }
    }

    private fun moveFocusOutOfList(action: () -> Unit) {
        allowLeavingList = true
        try {
            action()
        } finally {
            allowLeavingList = false
        }
    }

    private fun updateToolbarColors() {
        val textColor = getProperTextColor()
        val barColor = getProperBackgroundColor()
        binding.mainTopBar.setBackgroundColor(barColor)
        binding.mainTitle.setTextColor(textColor)
        binding.mainSearchInput.setTextColor(textColor)
        binding.mainSearchInput.setHintTextColor(textColor.adjustAlpha(0.5f))
        (toolbarButtons + binding.mainSearchClose).forEach { it.applyColorFilter(textColor) }
        window.statusBarColor = barColor
    }

    private fun showMoreMenu() {
        this.dpadPopupMenu(binding.mainMore).apply {
            inflate(R.menu.menu)
            refreshMenuItems(menu)
            setOnMenuItemClickListener { onMenuItemClicked(it) }
            show()
        }
    }

    private fun onMenuItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
            R.id.filter -> showFilterDialog()
            R.id.dialpad -> launchDialpad()
            R.id.change_view_type -> changeViewType()
            R.id.column_count -> changeColumnCount()
            R.id.create_new -> getCurrentFragment()?.fabClicked()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return false
        }
        return true
    }

    private fun openSearch() {
        if (!isSearchOpen) {
            getAllFragments().forEach { it?.finishActMode() }
            binding.mainToolbar.beGone()
            binding.mainSearchBar.beVisible()
        }

        binding.mainSearchInput.apply {
            setText("")
            moveFocusOutOfList { requestFocus() }
            showSearchKeyboard(this)
        }
        getCurrentFragment()?.onSearchQueryChanged("")
    }

    /** Connects the phone's keyboard (e.g. Traditional T9) right away, so it's active before the first key press. */
    private fun showSearchKeyboard(input: EditText, attemptsLeft: Int = 10) {
        input.postDelayed({
            if (!isSearchOpen || isDestroyed) {
                return@postDelayed
            }

            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (!input.hasWindowFocus() || !inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)) {
                if (attemptsLeft > 0) {
                    showSearchKeyboard(input, attemptsLeft - 1)
                }
            }
        }, 50)
    }

    private fun closeSearch() {
        if (!isSearchOpen) {
            return
        }

        hideKeyboard()
        binding.mainSearchInput.setText("")
        binding.mainSearchBar.beGone()
        binding.mainToolbar.beVisible()
        getAllFragments().forEach { it?.onSearchClosed() }
        binding.root.post {
            if (!focusCurrentList()) {
                binding.mainSearch.requestFocus()
            }
        }
    }

    private fun getCurrentQuery() = if (isSearchOpen) binding.mainSearchInput.text.toString() else ""

    /** Moves focus into the list of the visible tab, keeping [position] if given. */
    private fun focusCurrentList(position: Int = -1): Boolean {
        return getCurrentFragment()?.focusContent(position) == true
    }

    private fun focusTopBar() = moveFocusOutOfList {
        if (isSearchOpen) {
            binding.mainSearchInput.requestFocus()
        } else {
            topBarStart().requestFocus()
        }
    }

    /** The first top bar button: "Ab", or the search where there's nothing to jump between (Groups). */
    private fun topBarStart() = if (binding.mainLetters.visibility == View.VISIBLE) binding.mainLetters else binding.mainSearch

    private fun switchTab(delta: Int): Boolean {
        val target = binding.viewPager.currentItem + delta
        val count = binding.viewPager.adapter?.count ?: 0
        if (target !in 0 until count) {
            return true
        }

        // the focused row of the tab being left can't keep the focus, it would land on the top bar ("Ab" flashes);
        // the pager itself holds it until the new tab is shown
        val pager = binding.viewPager
        val wasFocusable = pager.isFocusable
        pager.isFocusable = true
        pager.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        pager.requestFocus()

        // each tab starts on its first item, not where it was left: scroll it up while it's still out of sight, so
        // it doesn't jump around once shown
        getFragmentAt(target)?.getListView()?.scrollToPosition(0)
        pager.setCurrentItem(target, false)
        pager.post {
            pager.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            pager.isFocusable = wasFocusable
            if (!focusCurrentList(0)) {
                focusTopBar()
            }
        }
        return true
    }

    private fun getFocusedContact(): Contact? {
        val fragment = getCurrentFragment() ?: return null
        if (fragment is GroupsFragment) {
            return null
        }

        val list = fragment.getListView()
        val position = list.focusedAdapterPosition()
        return (list.adapter as? org.fossify.contacts.adapters.ContactsAdapter)?.contactItems?.getOrNull(position)
    }

    private fun View.isInside(parent: View): Boolean {
        var current: View? = this
        while (current != null) {
            if (current == parent) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // open the popup and the letter grid on key up, otherwise the new window receives the key up and stops
        // reacting to keys
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                showMoreMenu()
            }
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_STAR && currentFocus !is EditText && getCurrentFragment() !is GroupsFragment) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                showLetterDialog()
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && handleKeyDown(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
        val focused = currentFocus
        val inEditText = focused is EditText
        val list = getCurrentFragment()?.getListView()
        val inList = focused != null && list != null && list.findContainingItemView(focused) != null
        val inPager = focused != null && focused.isInside(binding.viewPager)
        val inTopBar = focused != null && (focused in toolbarButtons || focused == binding.mainSearchClose || focused == binding.mainSearchInput)

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!inPager) {
                    return false
                }

                val grid = list?.layoutManager as? GridLayoutManager
                if (inList && grid != null && grid.spanCount > 1) {
                    val column = list.focusedAdapterPosition() % grid.spanCount
                    val atEdge = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) column == 0 else column == grid.spanCount - 1
                    if (!atEdge) {
                        return false
                    }
                }

                return switchTab(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (inTopBar) {
                    return true
                }

                if (inList) {
                    val position = list!!.focusedAdapterPosition()
                    val span = (list.layoutManager as? GridLayoutManager)?.spanCount ?: 1
                    if (position in 0 until span) {
                        focusTopBar()
                        return true
                    }
                } else if (inPager) {
                    focusTopBar()
                    return true
                }
                return false
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (inTopBar) {
                    focusCurrentList()
                    return true
                }
                return false
            }

            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (focused == binding.mainSearchInput) {
                    hideKeyboard()
                    focusCurrentList(0)
                    return true
                }
                return false
            }

            KeyEvent.KEYCODE_SEARCH -> {
                openSearch()
                return true
            }

            KeyEvent.KEYCODE_CALL -> {
                val contact = getFocusedContact() ?: return false
                callContact(contact)
                return true
            }

            KeyEvent.KEYCODE_POUND -> {
                if (inEditText) {
                    return false
                }
                val contact = getFocusedContact() ?: return false
                sendSmsToContact(contact)
                return true
            }
        }

        return false
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            findViewById<FavoritesFragment>(R.id.favorites_fragment)?.updateFavouritesAdapter()
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(org.fossify.commons.R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        DpadRadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                findViewById<FavoritesFragment>(R.id.favorites_fragment)?.columnCountChanged()
            }
        }
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowContactThumbnails = showContactThumbnails
            storedShowPhoneNumbers = showPhoneNumbers
            storedStartNameWithSurname = startNameWithSurname
            storedShowTabs = showTabs
            storedFontSize = fontSize
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val createNewContact = getCreateNewContactShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = Arrays.asList(createNewContact)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(org.fossify.commons.R.string.create_new_contact)
        val drawable = resources.getDrawable(org.fossify.commons.R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(org.fossify.commons.R.id.shortcut_plus_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, EditContactActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "create_new_contact")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getFragmentAt(binding.viewPager.currentItem)

    private fun getFragmentAt(index: Int): MyViewPagerFragment<*>? {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>>()
        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(findViewById(R.id.contacts_fragment))
        }

        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(findViewById(R.id.favorites_fragment))
        }

        if (showTabs and TAB_GROUPS != 0) {
            fragments.add(findViewById(R.id.groups_fragment))
        }

        return fragments.getOrNull(index)
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(org.fossify.commons.R.drawable.ic_person_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(org.fossify.commons.R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_GROUPS != 0) {
            icons.add(org.fossify.commons.R.drawable.ic_people_vector)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(org.fossify.commons.R.drawable.ic_person_outline_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(org.fossify.commons.R.drawable.ic_star_outline_vector)
        }

        if (showTabs and TAB_GROUPS != 0) {
            icons.add(org.fossify.commons.R.drawable.ic_people_outline_vector)
        }

        return icons
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                updateLettersButton()
                updateTabFocusability()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
            }
        })

        binding.viewPager.onGlobalLayout {
            refreshContacts(ALL_TABS_MASK)
            updateLettersButton()
            updateTabFocusability()
        }

        handleExternalIntent()
    }

    private fun handleExternalIntent() {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }

        if (uri != null) {
            tryImportContactsFromFile(uri) { success ->
                if (success) {
                    runOnUiThread {
                        refreshContacts(ALL_TABS_MASK)
                    }
                }
            }
            intent.action = null
        }
    }

    private fun setupTabs() {
        // a horizontal scroll view makes itself focusable whatever the layout says
        binding.mainTabsHolder.isFocusable = false
        binding.mainTabsHolder.isFocusableInTouchMode = false
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(org.fossify.commons.R.layout.bottom_tablayout_item).apply tab@{
                    customView?.let {
                        BottomTablayoutItemBinding.bind(it)
                    }?.apply {
                        tabItemIcon.setImageDrawable(getTabIcon(index))
                        tabItemLabel.text = getTabLabel(index)
                        AutofitHelper.create(tabItemLabel)

                        // icons only, to leave more room for contacts on small screens
                        tabItemLabel.beGone()
                        tabItemIcon.contentDescription = getTabLabel(index)
                        val iconSize = resources.getDimensionPixelSize(R.dimen.dpad_tab_icon_size)
                        tabItemIcon.layoutParams = tabItemIcon.layoutParams.apply {
                            width = iconSize
                            height = iconSize
                        }
                        (tabItemHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
                        binding.mainTabsHolder.addTab(this@tab)
                        // tabs switch with left/right, they are never a D-pad stop themselves
                        view.isFocusable = false
                        view.isFocusableInTouchMode = false
                    }
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                binding.viewPager.currentItem = it.position
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(getCurrentQuery())
                }
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    private fun launchDialpad() {
        hideKeyboardKeepingFocus()
        Intent(Intent.ACTION_DIAL).apply {
            try {
                startActivity(this)
            } catch (e: ActivityNotFoundException) {
                toast(org.fossify.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun launchSettings() {
        hideKeyboardKeepingFocus()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    /** Unlike Commons' hideKeyboard() this keeps the focus, which would otherwise flash on the top bar's "+". */
    private fun hideKeyboardKeepingFocus() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun launchAbout() {
        startActivity(Intent(this, DpadAboutActivity::class.java))
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing || isGettingContacts) {
            return
        }

        isGettingContacts = true

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = ViewPagerAdapter(this, tabsList, config.showTabs)
            binding.viewPager.currentItem = getDefaultTab()
        }

        val focusedPosition = getCurrentFragment()?.getListView()?.focusedAdapterPosition()?.takeIf { it >= 0 } ?: pausedListPosition
        val restoreFocus = pausedListPosition >= 0
        pausedListPosition = -1
        contactsChanged = false
        ContactsChanges.pending = false
        ensureBackgroundThread {
            // finding out the visible accounts queries the database, do it here instead of on the main thread
            val visibleSources = getVisibleContactSources()
            ContactsHelper(this).getContacts { contacts ->
                showContacts(contacts, visibleSources, refreshTabsMask, focusedPosition, restoreFocus)
            }
        }
    }

    private fun showContacts(
        contacts: ArrayList<Contact>,
        visibleSources: List<String>,
        refreshTabsMask: Int,
        focusedPosition: Int,
        restoreFocus: Boolean
    ) {
        run {
            isGettingContacts = false
            if (isDestroyed || isFinishing) {
                return
            }

            if (refreshTabsMask and TAB_CONTACTS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts, visibleSources = visibleSources)
                }
            }

            if (refreshTabsMask and TAB_FAVORITES != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts, visibleSources = visibleSources)
                }
            }

            if (refreshTabsMask and TAB_GROUPS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.groups_fragment)?.apply {
                    if (refreshTabsMask == TAB_GROUPS) {
                        skipHashComparing = true
                    }
                    refreshContacts(contacts, visibleSources = visibleSources)
                }
            }

            runOnUiThread {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(getCurrentQuery())
                }

                // the list gets redrawn, so put the focus back where it was
                binding.root.post {
                    val focused = currentFocus
                    if (focusGroupAfterRefresh != null) {
                        // handled by focusPendingGroup once the groups are loaded
                    } else if (focusTopBarOnStart) {
                        // at startup the focus is on the "Ab" button
                        focusTopBarOnStart = false
                        if (!isSearchOpen) {
                            focusTopBar()
                        }
                    } else if (restoreFocus || focused == null || !focused.isAttachedToWindow || focused == binding.viewPager) {
                        if (!focusCurrentList(focusedPosition) && !isSearchOpen) {
                            focusTopBar()
                        }
                    }
                }
            }
        }
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    private fun getAllFragments() = arrayListOf<MyViewPagerFragment<*>?>(
        findViewById(R.id.contacts_fragment),
        findViewById(R.id.favorites_fragment),
        findViewById(R.id.groups_fragment)
    )

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_GROUPS > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
