package org.fossify.contacts.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.isSPlus
import org.fossify.contacts.databinding.DialogDatePickerBinding
import org.joda.time.DateTime
import java.util.Calendar
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.contacts.helpers.isDpadDarkTheme
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.contacts.helpers.applyDpadFocusHighlight

private const val TYPING_TIMEOUT_MS = 1500L

class MyDatePickerDialog(val activity: BaseSimpleActivity, val defaultDate: String, val callback: (dateTag: String) -> Unit) {
    // the day / month / year columns take their text colors from this theme, light or dark like the app
    private val binding = DialogDatePickerBinding.inflate(
        LayoutInflater.from(
            ContextThemeWrapper(
                activity,
                if (activity.isDpadDarkTheme()) org.fossify.commons.R.style.MyDialogTheme_Dark else org.fossify.commons.R.style.MyDialogTheme
            )
        )
    )

    /** The day / month / year columns, left to right. */
    private val columns by lazy { binding.datePicker.findNumberPickers() }
    private var typedColumn: NumberPicker? = null
    private var typedDigits = ""
    private var lastDigitTime = 0L

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    val today = Calendar.getInstance()
                    var year = today.get(Calendar.YEAR)
                    var month = today.get(Calendar.MONTH)
                    var day = today.get(Calendar.DAY_OF_MONTH)

                    if (defaultDate.isNotEmpty()) {
                        val ignoreYear = defaultDate.startsWith("-")
                        binding.hideYear.isChecked = ignoreYear

                        if (ignoreYear) {
                            month = defaultDate.substring(2, 4).toInt() - 1
                            day = defaultDate.substring(5, 7).toInt()
                        } else {
                            year = defaultDate.substring(0, 4).toInt()
                            month = defaultDate.substring(5, 7).toInt() - 1
                            day = defaultDate.substring(8, 10).toInt()
                        }
                    }

                    if (activity.isDynamicTheme() && isSPlus()) {
                        val dialogBackgroundColor = activity.getColor(org.fossify.commons.R.color.you_dialog_background_color)
                        binding.dialogHolder.setBackgroundColor(dialogBackgroundColor)
                        binding.datePicker.setBackgroundColor(dialogBackgroundColor)
                    }

                    binding.datePicker.updateDate(year, month, day)
                    setupKeypadInput(alertDialog)
                }
            }
    }

    /**
     * Keypad phones: each column gets the focus highlight, the first one is focused on open, and digits can be typed
     * into the focused column (e.g. 1985 in the year column, 15 for the day, 8 for August).
     */
    private fun setupKeypadInput(alertDialog: AlertDialog) {
        columns.forEach { it.applyDpadFocusHighlight() }
        binding.hideYear.applyDpadFocusHighlight()
        if (!activity.isDynamicTheme()) {
            val backgroundColor = activity.getProperBackgroundColor()
            binding.dialogHolder.setBackgroundColor(backgroundColor)
            binding.datePicker.setBackgroundColor(backgroundColor)
            binding.hideYear.setColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), backgroundColor)
        }

        binding.datePicker.post { columns.firstOrNull()?.requestFocusFromTouch() }

        alertDialog.setOnKeyListener { _, keyCode, event ->
            // left/right walk one row: day → month → year → hide year → Cancel → OK (up/down change the values)
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                val chain = columns + listOfNotNull(
                    binding.hideYear,
                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ).filter { it.isShown }
                val index = chain.indexOf(alertDialog.currentFocus)
                if (index != -1) {
                    val next = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) index + 1 else index - 1
                    chain.getOrNull(next)?.requestFocus()
                    return@setOnKeyListener true
                }
            }

            val digit = keyCode - KeyEvent.KEYCODE_0
            val column = alertDialog.currentFocus as? NumberPicker
            if (event.action != KeyEvent.ACTION_DOWN || digit !in 0..9 || column == null) {
                return@setOnKeyListener false
            }

            val now = SystemClock.uptimeMillis()
            if (column != typedColumn || now - lastDigitTime > TYPING_TIMEOUT_MS) {
                typedDigits = ""
            }
            typedColumn = column
            lastDigitTime = now
            typedDigits += digit
            applyTypedValue(column)
            true
        }
    }

    private fun applyTypedValue(column: NumberPicker) {
        val picker = binding.datePicker
        var year = picker.year
        var month = picker.month
        var day = picker.dayOfMonth
        val typed = typedDigits.toInt()

        when {
            // the year column has a range of years, months are 0..11 shown as names, days are 1..28-31
            column.maxValue > 31 -> {
                if (typedDigits.length < 4) {
                    return
                }
                year = typed.coerceIn(column.minValue, column.maxValue)
                typedDigits = ""
            }

            column.maxValue == 11 -> {
                if (typed in 1..12) {
                    month = typed - 1
                }
                if (typedDigits.length >= 2 || typed > 1) {
                    typedDigits = ""
                }
            }

            else -> {
                if (typed in 1..31) {
                    day = typed
                }
                if (typedDigits.length >= 2 || typed > 3) {
                    typedDigits = ""
                }
            }
        }

        val maxDay = Calendar.getInstance().apply { set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        picker.updateDate(year, month, day.coerceAtMost(maxDay))
    }

    private fun View.findNumberPickers(): List<NumberPicker> {
        val result = ArrayList<NumberPicker>()
        fun collect(view: View) {
            if (view is NumberPicker) {
                result.add(view)
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collect(view.getChildAt(i))
                }
            }
        }
        collect(this)
        return result.filter { it.isShown }.sortedBy { IntArray(2).also { pos -> it.getLocationOnScreen(pos) }[0] }
    }

    private fun dialogConfirmed() {
        val year = binding.datePicker.year
        val month = binding.datePicker.month + 1
        val day = binding.datePicker.dayOfMonth
        val date = DateTime().withDate(year, month, day).withTimeAtStartOfDay()

        val tag = if (binding.hideYear.isChecked) {
            date.toString("--MM-dd")
        } else {
            date.toString("yyyy-MM-dd")
        }

        callback(tag)
    }
}
