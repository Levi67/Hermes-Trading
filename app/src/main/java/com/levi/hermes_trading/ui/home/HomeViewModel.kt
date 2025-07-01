package com.levi.hermes_trading.ui.home // Or your specific package for ViewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map // For LiveData transformations
import com.levi.hermes_trading.data.DataRepository // Adjust package if needed
import com.levi.hermes_trading.model.EtfEntry    // Adjust package if needed
import org.json.JSONObject

class HomeViewModel(private val dataRepository: DataRepository) : ViewModel() {

    init {
        Log.d("HomeViewModel", "HomeViewModel Initialized. DataRepository: $dataRepository")
        // Consider if an initial data load trigger is needed here or if the
        // repository handles initial load/caching, or if the fragment should trigger it.
        // For example, if data is not loaded on repository init, you might want:
        // refreshEtfData()
    }

    // LiveData for the raw JSON string, if needed for debugging or direct display
    val rawJsonStringData: LiveData<String?> = dataRepository.rawJsonFromSheet

    // LiveData for the parsed JSONObject from the repository
    // This is kept private if only used for the transformation below
    private val sheetJsonObject: LiveData<JSONObject?> = dataRepository.parsedSheetJsonObject

    // Transformed LiveData for the RecyclerView: List of EtfEntry objects
    val etfEntries: LiveData<List<EtfEntry>> = sheetJsonObject.map { jsonObject ->
        val entries = mutableListOf<EtfEntry>()
        jsonObject?.let { obj ->
            // Iterate over the keys in the main JSON object (e.g., "SPX", "DAX")
            obj.keys().forEach { key -> // 'key' here is "SPX", "DAX", etc.
                val innerObject = obj.optJSONObject(key) // Get the inner object for "SPX"
                if (innerObject != null) {
                    // Safely extract values, providing defaults if keys are missing
                    val value = innerObject.optString("value", "N/A")
                    val urgency = innerObject.optString("urgency", "Normal")
                    // Assuming color is a hex string like "#RRGGBBAA" or "#RRGGBB"
                    val color = innerObject.optString("color", "#808080") // Default to gray

                    entries.add(EtfEntry(name = key, value = value, urgency = urgency, color = color))
                } else {
                    Log.w("HomeViewModel", "Inner object for key '$key' was null in JSON.")
                }
            }
        }
        if (entries.isEmpty() && jsonObject != null && jsonObject.length() > 0) {
            Log.w("HomeViewModel", "Parsed 0 ETF entries, but JSON object was not null or empty. Check JSON structure and parsing logic.")
        } else {
            Log.d("HomeViewModel", "Parsed ${entries.size} ETF entries from JSONObject.")
        }
        entries // This list becomes the value of etfEntries LiveData
    }


    /**
     * Call this method to request the DataRepository to reload its data.
     * This function is typically called from the Fragment (e.g., on swipe-to-refresh
     * or when a broadcast indicates new data is available).
     */
    fun refreshEtfData() {
        Log.d("HomeViewModel", "refreshEtfData() called. Instructing DataRepository to reload.")
        // This is the method in your DataRepository that should:
        // 1. Fetch fresh data (e.g., from a file, network, etc.)
        // 2. Update the LiveData instances it exposes (rawJsonFromSheet, parsedSheetJsonObject)
        dataRepository.reloadDataFromFile() // Replace with your actual method name if different
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("HomeViewModel", "HomeViewModel onCleared.")
    }
}