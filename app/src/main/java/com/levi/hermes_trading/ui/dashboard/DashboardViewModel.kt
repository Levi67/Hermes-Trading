package com.levi.hermes_trading.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.levi.hermes_trading.JsonPull

class DashboardViewModel : ViewModel() {

    private val _text = MutableLiveData<String>()
    val text: LiveData<String> = _text

    private val jsonPull = JsonPull()

    init {
        fetchData()
    }

    fun refreshData() {
        fetchData()
    }

    private fun fetchData() {
        jsonPull.pullJson { rawJson ->
            _text.postValue(rawJson ?: "Failed to load JSON")
        }
    }
}
