package com.example.mypersonaltimetracker.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline creator: () -> VM,
): VM = viewModel(key = key, factory = viewModelFactory { initializer { creator() } })
