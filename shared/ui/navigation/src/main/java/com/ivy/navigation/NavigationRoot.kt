package com.ivy.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@SuppressLint("ComposeCompositionLocalUsage")
private val LocalNavigation = compositionLocalOf<Navigation> { error("No LocalNavigation") }

@Composable
fun NavigationRoot(
    navigation: Navigation,
    navGraph: @Composable (screen: Screen?) -> Unit
) {
    CompositionLocalProvider(
        LocalNavigation provides navigation,
    ) {
        // NOTE: Do NOT clear the ViewModelStore when landing on a non-legacy
        // screen. The store is the Activity's shared store, so clearing it
        // also destroys activity-level ViewModels (e.g. RootViewModel).
        // A recreated RootViewModel never re-runs start(), leaving its
        // appLocked state null forever - which RootActivity renders as a
        // permanent black screen. Screen-scoped ViewModels therefore live
        // for the Activity's lifetime, like the legacy screens' ViewModels.
        navGraph(navigation.currentScreen)
    }
}

@Composable
fun navigation(): Navigation {
    return LocalNavigation.current
}

/**
 * Provides a [ViewModel] instance from the shared (Activity) store.
 * Instances are kept for the Activity's lifetime: the store must never
 * be cleared on navigation because activity-level ViewModels (e.g.
 * RootViewModel) live in the same store. See the note in
 * [NavigationRoot].
 */
@Composable
inline fun <reified T : ViewModel> screenScopedViewModel(
    factory: ViewModelProvider.Factory? = null
): T {
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    requireNotNull(viewModelStoreOwner) { "No ViewModelStoreOwner provided" }
    val viewModelProvider = factory?.let {
        ViewModelProvider(viewModelStoreOwner, it)
    } ?: ViewModelProvider(viewModelStoreOwner)
    return viewModelProvider[T::class.java]
}
