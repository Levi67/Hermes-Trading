package com.levi.hermes_trading.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.levi.hermes_trading.JsonPull

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>("Loading...")
    val text: LiveData<String> = _text

    init {
        refreshData()
    }

    fun refreshData() {
        JsonPull().pullJson { cellValue ->
            _text.postValue(cellValue ?: "Failed to load data")
        }
    }
}