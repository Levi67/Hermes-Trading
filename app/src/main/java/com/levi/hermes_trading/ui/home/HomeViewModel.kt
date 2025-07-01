package com.levi.hermes_trading.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map // For LiveData transformations
import com.levi.hermes_trading.data.DataRepository
import com.levi.hermes_trading.model.EtfEntry // Your EtfEntry model
import org.json.JSONObject

class HomeViewModel(private val dataRepository: DataRepository) : ViewModel() {

    // Keep if you need raw string for debugging or other simple display
    val rawJsonStringData: LiveData<String?> = dataRepository.rawJsonFromSheet

    // The LiveData that will hold the parsed JSONObject from the repository
    private val sheetJsonObject: LiveData<JSONObject?> = dataRepository.parsedSheetJsonObject

    // Transformed LiveData for the RecyclerView
    val etfEntries: LiveData<List<EtfEntry>> = sheetJsonObject.map { jsonObject ->
        val entries = mutableListOf<EtfEntry>()
        jsonObject?.let { obj ->
            // Iterate over the keys in the main JSON object (e.g., "SPX", "DAX")
            obj.keys().forEach { key -> // 'key' here is "SPX", "DAX", etc.
                val innerObject = obj.optJSONObject(key) // Get the inner object for "SPX"
                if (innerObject != null) {
                    val value = innerObject.optString("value", "N/A")
                    val urgency = innerObject.optString("urgency", "Normal")
                    val color = innerObject.optString("color", "#80808080") // Default to gray

                    // 'key' is used as the 'name' for EtfEntry
                    entries.add(EtfEntry(name = key, value = value, urgency = urgency, color = color))
                }
            }
        }
        Log.d("HomeViewModel", "Parsed ${entries.size} ETF entries.")
        entries // This list becomes the value of etfEntries LiveData
    }

    init {
        Log.d("HomeViewModel", "Initialized. DataRepository: $dataRepository")
    }

    /**
     * Call this to ask the DataRepository to reload its data.
     */
    fun refreshEtfData() {
        Log.d("HomeViewModel", "refreshEtfData called, telling repository to reload.")
        dataRepository.reloadDataFromFile() // Or refreshDataFromWorkerCache()
    }
}