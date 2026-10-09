package org.fossify.contacts

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.views.MyAppBarLayout
import org.fossify.contacts.helpers.dpadCompactTopBar
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.core.content.ContextCompat
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.checkAppIconColor
import org.fossify.commons.helpers.IS_SYSTEM_THEME_ENABLED
import org.fossify.commons.helpers.PREFS_KEY
import org.fossify.commons.helpers.SHOW_PHONE_NUMBERS
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.contacts.helpers.applyDpadFocusHighlight
import org.fossify.contacts.helpers.ignoreTouches

private const val ICON_ALIASES_MIGRATED = "dumberkit_red_icon_v3"

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        useRedIconByDefault()
        useSystemThemeByDefault()
        showPhoneNumbersByDefault()
        // Commons calls any build without one of its unused images (removed by the release shrinker) a "fake version"
        // and sends people to its website; this app is built from its own source on purpose
        baseConfig.appSideloadingStatus = SIDELOADING_FALSE
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                // keys only: touches are ignored in every screen of the app
                activity.ignoreTouches()
                // Commons' screens we can't change directly: clear focus highlight and a focused first row
                if (activity is CustomizationActivity) {
                    activity.window.decorView.post {
                        setupCustomizationForKeys(activity)
                        prepareForKeys(activity)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * The red launcher entry is the default one (see the manifest), so Commons' icon color setting starts as red too,
     * otherwise its color picker and the launcher icon wouldn't match. Done once, the user's own choice later stays.
     */
    private fun useRedIconByDefault() {
        // Commons' check for old installs with an orange icon switches the icon to green, never wanted here
        baseConfig.wasOrangeIconChecked = true

        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        if (prefs.getBoolean(ICON_ALIASES_MIGRATED, false)) {
            return
        }

        val red = ContextCompat.getColor(this, org.fossify.commons.R.color.md_red_700)
        baseConfig.appIconColor = red
        prefs.edit().putBoolean(ICON_ALIASES_MIGRATED, true).apply()
        if (baseConfig.appId.isEmpty()) {
            // fresh install: the red launcher entry is already the enabled one (see the manifest)
            baseConfig.lastIconColor = red
        } else {
            // another entry is enabled: let Commons switch the launcher entries over to the red one
            baseConfig.lastIconColor = 0
            checkAppIconColor()
        }
    }

    /** The "System" color theme unless one was chosen; Commons only defaults to it on Android 12+. */
    private fun useSystemThemeByDefault() {
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        if (!prefs.contains(IS_SYSTEM_THEME_ENABLED)) {
            baseConfig.isSystemThemeEnabled = true
        }
    }

    /** The list shows the phone numbers under the names unless turned off in the settings. */
    private fun showPhoneNumbersByDefault() {
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        if (!prefs.contains(SHOW_PHONE_NUMBERS)) {
            baseConfig.showPhoneNumbers = true
        }
    }

    /** Compact always-colored top bar, and Cancel / Save rows at the end instead of the toolbar's check icon. */
    private fun setupCustomizationForKeys(activity: CustomizationActivity) {
        val appBar = activity.findViewById<MyAppBarLayout>(org.fossify.commons.R.id.app_bar)
        val scrollView = activity.findViewById<NestedScrollView>(org.fossify.commons.R.id.customization_nested_scrollview)
        if (appBar != null) {
            activity.dpadCompactTopBar(appBar, scrollView)
        }

        val holder = activity.findViewById<ViewGroup>(org.fossify.commons.R.id.customization_holder) ?: return
        if (holder.findViewById<View>(R.id.dpad_save_cancel_rows) == null) {
            val rows = LayoutInflater.from(activity).inflate(R.layout.dpad_save_cancel_rows, holder, false)
            holder.addView(rows)

            val toolbar = activity.findViewById<MaterialToolbar>(org.fossify.commons.R.id.customization_toolbar)
            rows.findViewById<View>(R.id.dpad_save_row_holder).setOnClickListener {
                toolbar?.menu?.performIdentifierAction(org.fossify.commons.R.id.save, 0)
            }
            // cancel = leave without saving, like choosing "discard" when going back
            rows.findViewById<View>(R.id.dpad_cancel_row_holder).setOnClickListener { activity.finish() }

            // Commons puts the back arrow and the check icon back after every color change, hide them again each time;
            // the hidden save item still works for the Save row (menus only check if an item is enabled)
            toolbar?.viewTreeObserver?.addOnGlobalLayoutListener {
                if (toolbar.navigationIcon != null) {
                    toolbar.navigationIcon = null
                }
                toolbar.menu.findItem(org.fossify.commons.R.id.save)?.takeIf { it.isVisible }?.isVisible = false
            }

            // a theme picked here (not saved yet) recolors only Commons' own labels, follow them
            val themeLabel = activity.findViewById<TextView>(org.fossify.commons.R.id.customization_theme_label)
            val labels = listOf(R.id.dpad_save_row_label, R.id.dpad_cancel_row_label).mapNotNull { rows.findViewById<TextView>(it) }
            holder.viewTreeObserver.addOnPreDrawListener {
                val color = themeLabel?.currentTextColor
                if (color != null) {
                    labels.filter { it.currentTextColor != color }.forEach { it.setTextColor(color) }
                }
                true
            }
        }

        holder.findViewById<ImageView>(R.id.dpad_save_row_icon)?.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_save_green))
        holder.findViewById<ImageView>(R.id.dpad_cancel_row_icon)?.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_cancel_red))
    }

    private fun prepareForKeys(activity: Activity) {
        val rows = ArrayList<View>()
        fun collect(view: View) {
            // hidden rows too: some only show up after picking a theme (text, background, primary color)
            if (view !is EditText && view.isClickable && view.id != View.NO_ID &&
                activity.resources.getResourceEntryName(view.id).endsWith("_holder")
            ) {
                rows.add(view)
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collect(view.getChildAt(i))
                }
            }
        }
        collect(activity.window.decorView)

        rows.forEach {
            it.isFocusable = true
            it.applyDpadFocusHighlight()
        }

        val focused = activity.currentFocus
        if (focused == null || focused is ScrollView || focused is NestedScrollView) {
            rows.firstOrNull { it.isShown }?.requestFocusFromTouch()
        }
    }
}
