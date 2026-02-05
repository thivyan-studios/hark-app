package com.thivyanstudios.hark.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.util.Constants.Navigation

sealed class Screen(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    data object Home : Screen(Navigation.ROUTE_HOME, R.string.nav_home, R.drawable.ic_home)
    data object Settings : Screen(Navigation.ROUTE_SETTINGS, R.string.nav_settings, R.drawable.ic_settings)
}
