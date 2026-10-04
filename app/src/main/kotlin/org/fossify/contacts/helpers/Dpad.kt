package org.fossify.contacts.helpers

import org.fossify.commons.extensions.getProperTextColor
import android.widget.TextView
import android.view.Gravity
import android.util.TypedValue
import android.graphics.drawable.InsetDrawable
import android.graphics.Paint
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.NestedScrollView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getColoredMaterialStatusBarColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.views.MyAppBarLayout
import org.fossify.commons.extensions.getProperBackgroundColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.normalizeString
import org.fossify.contacts.R

/**
 * Helpers for keypad / D-pad phones (Qin F21, Dumber Mini, Doov R77 Pro…).
 */

/** Clearly visible highlight for the focused item, used as a View foreground. */
fun Context.dpadFocusForeground(): Drawable {
    val primary = getProperPrimaryColor()
    val strokeWidth = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
    val focused = GradientDrawable().apply {
        setColor(ColorUtils.setAlphaComponent(primary, 0x38))
        setStroke(strokeWidth, primary)
        cornerRadius = 4 * resources.displayMetrics.density
    }

    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), focused)
        addState(intArrayOf(), ColorDrawable(0))
    }
}

fun View.applyDpadFocusHighlight() {
    foreground = context.dpadFocusForeground()
}

private val T9_KEYS = mapOf(
    'a' to '2', 'b' to '2', 'c' to '2',
    'd' to '3', 'e' to '3', 'f' to '3',
    'g' to '4', 'h' to '4', 'i' to '4',
    'j' to '5', 'k' to '5', 'l' to '5',
    'm' to '6', 'n' to '6', 'o' to '6',
    'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
    't' to '8', 'u' to '8', 'v' to '8',
    'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
    ' ' to '0'
)

/** "Ján Novák" -> "526066825" so digit queries can match names like on old phones. */
fun String.toT9(): String = normalizeString().lowercase().map { T9_KEYS[it] ?: it }.joinToString("")

/** True if the full name or any of its words starts with the T9 digit sequence, like smart dial on old phones. */
fun String.matchesT9(digits: String): Boolean {
    if (digits.isEmpty() || !digits.all { it in '0'..'9' }) {
        return false
    }

    val t9 = toT9()
    return t9.startsWith(digits) || t9.split('0').any { it.startsWith(digits) }
}

fun RecyclerView.focusedAdapterPosition(): Int {
    val focused = findFocus() ?: return RecyclerView.NO_POSITION
    val item = findContainingItemView(focused) ?: return RecyclerView.NO_POSITION
    return getChildAdapterPosition(item)
}

/**
 * Focuses [position] (or the first visible item), scrolling to it and waiting for it to be laid out if needed.
 * Returns false if the list is empty.
 */
fun RecyclerView.focusItem(position: Int = RecyclerView.NO_POSITION): Boolean {
    val count = adapter?.itemCount ?: 0
    if (count == 0 || visibility != View.VISIBLE) {
        return false
    }

    val target = if (position in 0 until count) {
        position
    } else {
        (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
    }

    if (findViewHolderForAdapterPosition(target) == null) {
        scrollToPosition(target)
    }

    fun tryFocus(attemptsLeft: Int) {
        val holder = findViewHolderForAdapterPosition(target)
        when {
            // requestFocusFromTouch also leaves touch mode, list items can't take focus in it
            holder != null -> holder.itemView.requestFocusFromTouch()
            attemptsLeft > 0 -> postDelayed({ tryFocus(attemptsLeft - 1) }, FOCUS_RETRY_DELAY_MS)
        }
    }

    tryFocus(FOCUS_RETRY_ATTEMPTS)
    return true
}

private const val FOCUS_RETRY_ATTEMPTS = 20
private const val FOCUS_RETRY_DELAY_MS = 50L

/** Light or dark theme for popups/dialogs, following the app's background color (or Material You). */
fun Context.isDpadDarkTheme(): Boolean = getProperBackgroundColor().getContrastColor() == Color.WHITE

fun Context.dpadPopupTheme(): Int = if (isDpadDarkTheme()) R.style.DpadPopupOverlay_Dark else R.style.DpadPopupOverlay_Light

/** A popup menu matching the app's light/dark colors (the plain one is always dark). */
fun Context.dpadPopupMenu(anchor: View) = PopupMenu(ContextThemeWrapper(this, dpadPopupTheme()), anchor)

/**
 * Top bar for keypad phones: always filled with the colored top bar color (instead of turning colored only when
 * scrolled), 40dp high with a smaller title, and no back arrow (the phone has a Back key).
 */
fun BaseSimpleActivity.dpadCompactTopBar(appBar: MyAppBarLayout, scrollingView: NestedScrollView?) {
    scrollingView?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
    appBar.toolbar?.apply {
        navigationIcon = null
        layoutParams = layoutParams.apply { height = resources.getDimensionPixelSize(R.dimen.dpad_toolbar_height) }
        minimumHeight = 0
        setTitleTextAppearance(context, R.style.DpadTopBarTitle)
    }
    updateTopBarColors(appBar, getColoredMaterialStatusBarColor())
}

/**
 * One-line text fields in dialogs keep the up/down arrows for the text cursor, so the focus never got to the rows or
 * buttons below them: move the focus with up/down instead (left/right still move the cursor).
 */
fun View.moveFocusOnUpDown() {
    setOnKeyListener { view, keyCode, event ->
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            else -> return@setOnKeyListener false
        }
        val next = view.focusSearch(direction) ?: if (direction == View.FOCUS_DOWN) view.focusSearch(View.FOCUS_FORWARD) else null
        if (next == null || next == view) {
            return@setOnKeyListener false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            next.requestFocus()
        }
        true
    }
}

/**
 * The "add" action of an empty tab or group as a round "+" button instead of an underlined text link; [label] is read
 * out by accessibility. Focused, it gets a round frame.
 */
fun TextView.makeRoundAddButton(label: String) {
    val density = resources.displayMetrics.density
    val primary = context.getProperPrimaryColor()
    contentDescription = label
    text = "+"
    paintFlags = paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
    includeFontPadding = false
    gravity = Gravity.CENTER
    setPadding(0, 0, 0, (2 * density).toInt())
    setTextColor(primary.getContrastColor())
    isFocusable = true

    val size = resources.getDimensionPixelSize(R.dimen.dpad_round_add_button_size)
    layoutParams = layoutParams?.apply {
        width = size
        height = size
    }

    val circle = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(primary)
    }
    background = InsetDrawable(circle, (6 * density).toInt())
    foreground = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((3 * density).toInt(), context.getProperTextColor())
        })
    }
}
