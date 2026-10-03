package com.dazaike.photovault.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dazaike.photovault.R
import com.dazaike.photovault.ui.theme.LocalContentColor

/** Stroked 24dp icon set (1.75 stroke, round caps), tinted at runtime like SVG `currentColor`. */
object PrismIcons {
    @DrawableRes val ArrowUp = R.drawable.ic_arrow_up
    @DrawableRes val ArrowDown = R.drawable.ic_arrow_down
    @DrawableRes val ArrowLeft = R.drawable.ic_arrow_left
    @DrawableRes val ArrowRight = R.drawable.ic_arrow_right
    @DrawableRes val ChevronUp = R.drawable.ic_chevron_up
    @DrawableRes val ChevronDown = R.drawable.ic_chevron_down
    @DrawableRes val ChevronLeft = R.drawable.ic_chevron_left
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val Menu = R.drawable.ic_menu
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val Plus = R.drawable.ic_plus
    @DrawableRes val Minus = R.drawable.ic_minus
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val Sliders = R.drawable.ic_sliders
    @DrawableRes val Eye = R.drawable.ic_eye
    @DrawableRes val EyeOff = R.drawable.ic_eye_off
    @DrawableRes val Alert = R.drawable.ic_alert
    @DrawableRes val Info = R.drawable.ic_info
    @DrawableRes val Trash = R.drawable.ic_trash
    @DrawableRes val History = R.drawable.ic_history
    @DrawableRes val Backspace = R.drawable.ic_backspace
    @DrawableRes val Text = R.drawable.ic_text
    @DrawableRes val Grid = R.drawable.ic_grid
    @DrawableRes val Bell = R.drawable.ic_bell
    @DrawableRes val Sidebar = R.drawable.ic_sidebar

    // PhotoVault additions (same 24dp / 1.75 stroke / round-cap style)
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val Pause = R.drawable.ic_pause
    @DrawableRes val Share = R.drawable.ic_share
    @DrawableRes val Download = R.drawable.ic_download
    @DrawableRes val Copy = R.drawable.ic_copy
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val Folder = R.drawable.ic_folder
    @DrawableRes val FolderPlus = R.drawable.ic_folder_plus
    @DrawableRes val FolderMinus = R.drawable.ic_folder_minus
    @DrawableRes val Restore = R.drawable.ic_restore
    @DrawableRes val More = R.drawable.ic_more
    @DrawableRes val SelectAll = R.drawable.ic_select_all
    @DrawableRes val Timer = R.drawable.ic_timer
    @DrawableRes val Photo = R.drawable.ic_photo
    @DrawableRes val Circle = R.drawable.ic_circle
    @DrawableRes val CheckCircle = R.drawable.ic_check_circle
    @DrawableRes val Calendar = R.drawable.ic_calendar

    val all: List<Pair<String, Int>> = listOf(
        "arrow-up" to ArrowUp,
        "arrow-down" to ArrowDown,
        "arrow-left" to ArrowLeft,
        "arrow-right" to ArrowRight,
        "chevron-up" to ChevronUp,
        "chevron-down" to ChevronDown,
        "chevron-left" to ChevronLeft,
        "chevron-right" to ChevronRight,
        "search" to Search,
        "close" to Close,
        "menu" to Menu,
        "check" to Check,
        "plus" to Plus,
        "minus" to Minus,
        "settings" to Settings,
        "sliders" to Sliders,
        "eye" to Eye,
        "eye-off" to EyeOff,
        "alert" to Alert,
        "info" to Info,
        "trash" to Trash,
        "history" to History,
        "backspace" to Backspace,
        "text" to Text,
        "grid" to Grid,
        "bell" to Bell,
        "sidebar" to Sidebar,
    )
}

@Composable
fun PrismIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
