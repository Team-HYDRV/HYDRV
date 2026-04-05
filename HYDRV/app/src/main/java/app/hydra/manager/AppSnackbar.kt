package app.hydra.manager

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.snackbar.Snackbar

object AppSnackbar {

    private const val DEFAULT_BOTTOM_MARGIN_DP = 80
    private const val ANCHORED_BOTTOM_MARGIN_DP = 10
    private const val SIDE_MARGIN_DP = 24

    fun show(
        anchorView: View,
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        extraBottomMarginPx: Int = 0,
        anchorTarget: View? = null,
        baseBottomMarginDp: Int = DEFAULT_BOTTOM_MARGIN_DP,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ): Snackbar {
        val context = anchorView.context

        val snackbar = Snackbar.make(anchorView, message, duration)
        val resolvedAnchor = resolveAnchorTarget(anchorView, anchorTarget)
        resolvedAnchor?.let(snackbar::setAnchorView)
        applyMargins(
            snackbar,
            extraBottomMarginPx,
            if (resolvedAnchor != null) ANCHORED_BOTTOM_MARGIN_DP else baseBottomMarginDp
        )

        snackbar.view.background = ContextCompat.getDrawable(context, R.drawable.card)
        snackbar.view.minimumHeight = 0

        snackbar.view.findViewById<TextView>(MaterialR.id.snackbar_text)?.apply {
            setTextColor(
                ThemeColors.color(
                    context,
                    MaterialR.attr.colorOnSurface,
                    R.color.text
                )
            )
            maxLines = 2
        }
        snackbar.setActionTextColor(
                ThemeColors.color(
                    context,
                    androidx.appcompat.R.attr.colorPrimary,
                    R.color.accent
                )
            )
        if (!actionLabel.isNullOrBlank() && action != null) {
            snackbar.setAction(actionLabel) { action() }
        }

        snackbar.show()
        return snackbar
    }

    private fun resolveAnchorTarget(anchorView: View, explicitAnchor: View?): View? {
        if (explicitAnchor != null) return explicitAnchor
        return anchorView.rootView?.findViewById(R.id.bottomNav)
    }

    fun updateBottomMargin(
        snackbar: Snackbar,
        extraBottomMarginPx: Int = 0,
        baseBottomMarginDp: Int = DEFAULT_BOTTOM_MARGIN_DP
    ) {
        applyMargins(snackbar, extraBottomMarginPx, baseBottomMarginDp)
    }

    private fun applyMargins(
        snackbar: Snackbar,
        extraBottomMarginPx: Int,
        baseBottomMarginDp: Int
    ) {
        val density = snackbar.view.context.resources.displayMetrics.density
        val params = snackbar.view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.bottomMargin = (baseBottomMarginDp * density).toInt() + extraBottomMarginPx
        params.leftMargin = (SIDE_MARGIN_DP * density).toInt()
        params.rightMargin = (SIDE_MARGIN_DP * density).toInt()
        snackbar.view.layoutParams = params
    }
}
