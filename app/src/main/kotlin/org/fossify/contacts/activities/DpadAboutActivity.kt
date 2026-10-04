package org.fossify.contacts.activities

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.views.MyTextView
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.databinding.ActivityDpadAboutBinding
import org.fossify.contacts.helpers.dpadCompactTopBar

/** App name, version, the keypad shortcuts and the license; the arrows scroll the page. */
class DpadAboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDpadAboutBinding::inflate)

    private val shortcuts = listOf(
        R.string.dpad_key_ok to R.string.dpad_key_ok_action,
        R.string.dpad_key_ok_hold to R.string.dpad_key_ok_hold_action,
        R.string.dpad_key_call to R.string.dpad_key_call_action,
        R.string.dpad_key_pound to R.string.dpad_key_pound_action,
        R.string.dpad_key_star to R.string.dpad_key_star_action,
        R.string.dpad_key_left_right to R.string.dpad_key_left_right_action,
        R.string.dpad_key_menu to R.string.dpad_key_menu_action,
        R.string.dpad_key_back to R.string.dpad_key_back_action
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.aboutNestedScrollview))

        binding.aboutVersion.text = getString(R.string.dpad_about_version, BuildConfig.VERSION_NAME)
        binding.aboutIcon.setImageResource(getAppIconIDs().getOrNull(getAppIconColors().indexOf(baseConfig.appIconColor)) ?: R.mipmap.ic_launcher_red)
        addShortcuts()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.None)
        dpadCompactTopBar(binding.aboutAppbar, binding.aboutNestedScrollview)
        updateTextColors(binding.aboutHolder)
        listOf(binding.aboutShortcutsLabel, binding.aboutLicenseLabel).forEach { it.setTextColor(getProperPrimaryColor()) }
        binding.aboutNestedScrollview.requestFocus()
    }

    private fun addShortcuts() {
        val keyWidth = resources.getDimensionPixelSize(R.dimen.dpad_about_key_width)
        shortcuts.forEach { (key, action) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin), 0, 0)
            }

            row.addView(MyTextView(this).apply {
                setText(key)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.START
            }, LinearLayout.LayoutParams(keyWidth, LinearLayout.LayoutParams.WRAP_CONTENT))

            row.addView(MyTextView(this).apply {
                setText(action)
                textSize = 15f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            binding.aboutShortcuts.addView(row)
        }
    }
}
