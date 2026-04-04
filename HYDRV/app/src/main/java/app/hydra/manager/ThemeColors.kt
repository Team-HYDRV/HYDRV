package app.hydra.manager

import android.content.Context
import android.content.res.ColorStateList
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

object ThemeColors {
    fun color(context: Context, @AttrRes attr: Int, @ColorRes fallback: Int): Int {
        return MaterialColors.getColor(context, attr, ContextCompat.getColor(context, fallback))
    }

    fun colorStateList(context: Context, @AttrRes attr: Int, @ColorRes fallback: Int): ColorStateList {
        return ColorStateList.valueOf(color(context, attr, fallback))
    }
}
