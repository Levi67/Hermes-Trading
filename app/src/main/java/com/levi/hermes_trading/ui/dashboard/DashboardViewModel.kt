package com.levi.hermes_trading.ui.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map // For LiveData transformations
import com.levi.hermes_trading.data.DataRepository
import org.json.JSONObject

class DashboardViewModel(private val dataRepository: DataRepository) : ViewModel() {

    // Keep rawJsonStringData if you might want to display it directly or for debugging
    val rawJsonStringData: LiveData<String?> = dataRepository.rawJsonFromSheet

    // LiveData for the parsed JSONObject
    private val sheetJsonObjectInternal: LiveData<JSONObject?> = dataRepository.parsedSheetJsonObject

    // Transformed LiveData: A pretty-printed string of the JSONObject for the TextView
    val dashboardDisplayText: LiveData<String> = sheetJsonObjectInternal.map { jsonObject ->
        if (jsonObject != null) {
            try {
                jsonObject.toString(2) // Pretty print with an indent of 2 spaces
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error converting JSONObject to string", e)
                "Error displaying JSON content."
            }
        } else {
            "No data available or data is not valid JSON."
        }
    }

    init {
        Log.d("DashboardViewModel", "Initialized.")
    }

    fun someDashboardSpecificLogic() {
        Log.d("DashboardViewModel", "Specific logic. Current JSON: ${sheetJsonObjectInternal.value?.toString(2)}")
    }

    fun refreshDisplayedData() {
        Log.d("DashboardViewModel", "refreshDisplayedData called, telling repository to reload.")
        dataRepository.reloadDataFromFile()
    }
}