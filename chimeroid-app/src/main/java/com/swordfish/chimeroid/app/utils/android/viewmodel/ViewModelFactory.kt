package com.swordfish.chimeroid.app.utils.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

inline fun <VM : ViewModel> viewModelFactory(crossinline creator: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }
