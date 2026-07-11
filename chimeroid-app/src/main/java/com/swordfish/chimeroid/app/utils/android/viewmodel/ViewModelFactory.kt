package com.swordfish.chimeroid.app.utils.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Builds a [ViewModelProvider.Factory] from a single [creator] lambda.
 *
 * Every feature module previously duplicated the same boilerplate:
 * ```
 * override fun <T : ViewModel> create(modelClass: Class<T>): T {
 *     return XxxViewModel(args) as T
 * }
 * ```
 * Delegating a `Factory` class to `viewModelFactory { XxxViewModel(args) }` keeps the exact
 * same public constructor/type (no call-site changes anywhere) while removing the repeated
 * override + unchecked cast from every ViewModel.
 */
inline fun <VM : ViewModel> viewModelFactory(crossinline creator: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }
