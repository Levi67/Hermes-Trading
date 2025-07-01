package com.levi.hermes_trading.viewmodel // Or your common viewmodel/util package

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.levi.hermes_trading.data.* // Adjust import
import com.levi.hermes_trading.ui.home.HomeViewModel // Example, add other ViewModels below
import com.levi.hermes_trading.ui.dashboard.DashboardViewModel // Example

/**
 * A ViewModel Factory for ViewModels that require a DataRepository.
 * The DataRepository itself requires an Application context.
 */
class DataViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(DataRepository(application)) as T
        }
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(DataRepository(application)) as T
        }
        // Add more 'if' blocks here for other ViewModels that need DataRepository
        // e.g., if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) { ... }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}