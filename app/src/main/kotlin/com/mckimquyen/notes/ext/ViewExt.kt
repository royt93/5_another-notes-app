package com.mckimquyen.notes.ext

import android.content.Context
import android.graphics.Paint
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.R as RMaterial

/**
 * Try to hide the keyboard from [this] view.
 */
fun View.hideKeyboard() {
    val context = this.context ?: return
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}

/**
 * Try to show the keyboard from [this] view.
 * The keyboard is shown with a 200 ms delay by default, otherwise it often doesn't work.
 */
fun View.showKeyboard(delay: Long = 200L) {
    val context = this.context ?: return
    this.postDelayed({
        val focus = this.findFocus() ?: return@postDelayed
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(focus, 0)
    }, delay)
}

/**
 * [this]'s background is normally a [MaterialShapeDrawable] (set by a MaterialToolbar/
 * AppBarLayout theme), but a customized ROM/theme could swap it for a plain ColorDrawable/
 * GradientDrawable — fall back to the theme's colorSurface instead of crashing callers that
 * need this view's effective tint color (e.g. status bar color transitions on Action Mode
 * enter/exit). Shared by NoteFrm/LabelFrm, previously duplicated in both. FIX-L10.
 */
fun View.materialShapeTintOrSurfaceColor(): Int {
    return (background as? MaterialShapeDrawable)?.resolvedTintColor
        ?: MaterialColors.getColor(this, RMaterial.attr.colorSurface)
}

/**
 * Whether to draw strikethrough on text or not.
 */
var TextView.strikethroughText: Boolean
    get() = (this.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG) != 0
    set(value) {
        this.paintFlags = if (value) {
            this.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            this.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }
