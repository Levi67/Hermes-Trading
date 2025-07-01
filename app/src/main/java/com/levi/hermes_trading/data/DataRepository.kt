package com.levi.hermes_trading.data // Or your repository package

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.levi.hermes_trading.JsonPull // Assuming JsonPull is in this package
import com.levi.hermes_trading.JsonUpdateWorker // Assuming JsonUpdateWorker is in this package
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class DataRepository(private val context: Context) {

    private val _rawJsonFromSheet = MutableLiveData<String?>()
    val rawJsonFromSheet: LiveData<String?> = _rawJsonFromSheet

    private val _parsedSheetJsonObject = MutableLiveData<JSONObject?>()
    val parsedSheetJsonObject: LiveData<JSONObject?> = _parsedSheetJsonObject

    private val jsonPullUtil = JsonPull()

    init {
        Log.d("DataRepository", "Initializing and loading initial data...")
        loadInitialData()
    }

    private fun loadInitialData() {
        CoroutineScope(Dispatchers.IO).launch {
            var dataToLoad = JsonUpdateWorker.latestRawJsonStringFromSheet
            var source = "In-memory cache (Worker)"

            if (dataToLoad == null) {
                Log.d("DataRepository", "Worker cache miss, attempting to load from file.")
                dataToLoad = loadJsonStringFromFile()
                source = "File storage"
            }

            val parsedJson = if (dataToLoad != null) {
                Log.d("DataRepository", "Data loaded from $source for initial load.")
                if (JsonUpdateWorker.latestRawJsonStringFromSheet == null) {
                    JsonUpdateWorker.latestRawJsonStringFromSheet = dataToLoad
                }
                try {
                    jsonPullUtil.parseJson(dataToLoad) // Parse here
                } catch (e: JSONException) {
                    Log.e("DataRepository", "Failed to parse JSON during initial load: ${e.message}")
                    null
                }
            } else {
                Log.d("DataRepository", "No data found in worker cache or file for initial load.")
                null
            }

            withContext(Dispatchers.Main) {
                _rawJsonFromSheet.value = dataToLoad
                _parsedSheetJsonObject.value = parsedJson
            }
        }
    }

    private fun loadJsonStringFromFile(): String? {
        return try {
            val file = File(context.filesDir, JsonUpdateWorker.JSON_FILENAME)
            if (file.exists() && file.canRead()) {
                val content = FileInputStream(file).bufferedReader().use { it.readText() }
                if (content.isNotBlank()) {
                    Log.d("DataRepository", "Successfully loaded JSON from file: ${JsonUpdateWorker.JSON_FILENAME}")
                    content
                } else {
                    Log.w("DataRepository", "File exists but is empty: ${JsonUpdateWorker.JSON_FILENAME}")
                    null
                }
            } else {
                Log.d("DataRepository", "File not found or not readable: ${JsonUpdateWorker.JSON_FILENAME}")
                null
            }
        } catch (e: IOException) {
            Log.e("DataRepository", "Error loading JSON from file", e)
            null
        }
    }

    private suspend fun saveJsonStringToFile(jsonString: String): Boolean {
        return try {
            withContext(Dispatchers.IO) { // Ensure file operations are on IO thread
                val file = File(context.filesDir, JsonUpdateWorker.JSON_FILENAME)
                FileOutputStream(file).use {
                    it.write(jsonString.toByteArray())
                }
                Log.d("DataRepository", "Successfully saved JSON to file: ${JsonUpdateWorker.JSON_FILENAME}")
            }
            true
        } catch (e: IOException) {
            Log.e("DataRepository", "Error saving JSON to file", e)
            false
        }
    }

    /**
     * Fetches fresh data from the network, saves it to a local file,
     * updates the in-memory cache, and updates the LiveData instances.
     * It also checks for any "alarm-active": true flags in the fetched JSON.
     *
     * @return FetchResult indicating overall success and a list of item names with active alarms.
     */
    suspend fun fetchAndRefreshAllDataSources(): FetchResult {
        Log.i("DataRepository", "Attempting to fetch and refresh all data sources...")
        return withContext(Dispatchers.IO) { // Main execution context for this function
            val fetchedJsonString = fetchJsonStringFromNetwork()

            if (fetchedJsonString == null) {
                Log.w("DataRepository", "Network fetch returned null or failed.")
                return@withContext FetchResult(isSuccess = false)
            }

            Log.d("DataRepository", "Successfully fetched new JSON data from network.")
            val saveSuccess = saveJsonStringToFile(fetchedJsonString)
            if (!saveSuccess) {
                Log.e("DataRepository", "Failed to save fetched JSON to file.")
                return@withContext FetchResult(isSuccess = false)
            }

            JsonUpdateWorker.latestRawJsonStringFromSheet = fetchedJsonString
            Log.d("DataRepository", "Updated JsonUpdateWorker's static cache.")

            val parsedData: JSONObject? = try {
                jsonPullUtil.parseJson(fetchedJsonString)
            } catch (e: JSONException) {
                Log.e("DataRepository", "Fetched JSON string could not be parsed by JsonPull: ${e.message}")
                null
            }

            val activeAlarms = mutableListOf<String>()
            if (parsedData != null) {
                parsedData.keys().forEach { itemName ->
                    try {
                        val itemObject = parsedData.optJSONObject(itemName)
                        if (itemObject != null) {
                            // Assuming "alarm-active" is a boolean (true/false) in your JSON now
                            val isAlarmActive = itemObject.optBoolean("alarm-active", false)
                            if (isAlarmActive) {
                                activeAlarms.add(itemName)
                                Log.i("DataRepository", "ALARM ACTIVE for item: $itemName")
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("DataRepository", "Error processing alarm status for item '$itemName': ${e.message}", e)
                        // Continue to next item
                    }
                }
            } else {
                Log.w("DataRepository", "Parsed data was null, cannot check for alarms.")
            }

            // Update LiveData on the Main thread
            withContext(Dispatchers.Main) {
                _rawJsonFromSheet.value = fetchedJsonString
                _parsedSheetJsonObject.value = parsedData // Use the already parsed data
                Log.i("DataRepository", "LiveData updated with new data from network.")
            }

            FetchResult(isSuccess = true, activeAlarmItems = activeAlarms)
        }
    }

    /**
     * Fetches the content of a specific cell from Google Sheets using Sheets API v4.
     * The cell value itself is assumed to be the JSON string needed by the app.
     * This function is suspendable as it performs network operations.
     */
    private fun fetchJsonStringFromNetwork(): String? {
        // --- Configuration Variables ---
        // It's highly recommended to move these to a configuration file or BuildConfig fields
        // and NOT hardcode them directly, especially the API key.
        val sheetId = "1YtcG_4Vp8XGMlaza-CkZCoxPC_C57twUCdZzH8gEI9g"
        val sheetName = "Sheet1" // <<< CRITICAL: VERIFY THIS MATCHES YOUR SHEET TAB NAME EXACTLY (CASE-SENSITIVE)
        val cellRange = "C3"

        // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
        // CRITICAL SECURITY WARNING: This API key is hardcoded and has been shared.
        // 1. You MUST regenerate this API key in your Google Cloud Console IMMEDIATELY.
        // 2. Replace the key below with your NEW, REGENERATED key.
        // 3. Restrict the NEW key (Application restrictions: Android app, API restrictions: Google Sheets API).
        // 4. For production, use secure methods to store API keys (e.g., local.properties + BuildConfig).
        val apiKey = "AIzaSyCvsSkEFWlAa0Cjk62mW5PfnteYxYbljTo" // <<< REPLACE WITH YOUR NEW, REGENERATED KEY
        // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---

        val baseUrl = "https://sheets.googleapis.com/v4/spreadsheets"
        val urlString = "$baseUrl/$sheetId/values/$sheetName!$cellRange?key=$apiKey"

        if (sheetId.isBlank() || sheetName.isBlank() || cellRange.isBlank() || apiKey.isBlank() /* Example placeholder check */) {
            Log.e("DataRepository", "Network configuration is incomplete or uses placeholder API Key for $sheetName!$cellRange. Cannot fetch.")
            return null
        }

        Log.i("DataRepository", "Fetching from URL: $urlString")

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000   // 15 seconds

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val apiResponseJsonString = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("DataRepository", "Raw API Response for $sheetName!$cellRange: $apiResponseJsonString")

                if (apiResponseJsonString.isBlank()) {
                    Log.w("DataRepository", "Network response (API wrapper) was empty for $sheetName!$cellRange.")
                    return null
                }

                // Parse the Google Sheets API response to extract the cell's actual content
                try {
                    val jsonApiResponse = JSONObject(apiResponseJsonString)
                    val valuesArray = jsonApiResponse.optJSONArray("values")
                    if (valuesArray != null && valuesArray.length() > 0) {
                        val firstRowArray = valuesArray.optJSONArray(0)
                        if (firstRowArray != null && firstRowArray.length() > 0) {
                            val cellContentString = firstRowArray.optString(0, null) // Get the string from the first cell of the first row
                            if (cellContentString != null) {
                                // Validate if the cell content itself is valid JSON
                                try {
                                    JSONObject(cellContentString) // This just checks if it's parsable as JSON
                                    Log.d("DataRepository", "Extracted valid cell content (expected JSON string) from $sheetName!$cellRange: $cellContentString")
                                    return cellContentString // Return the cell content, which is our target JSON string
                                } catch (e: JSONException) {
                                    Log.e("DataRepository", "Content of cell $sheetName!$cellRange ('$cellContentString') is NOT valid JSON: ${e.message}")
                                    return null // Cell content wasn't the expected JSON format
                                }
                            } else {
                                Log.w("DataRepository", "Cell $sheetName!$cellRange has no value or is not a string in API response.")
                                return null // No string content in the cell
                            }
                        }
                    }
                    // If we reach here, 'values' or 'firstRowArray' was missing/empty or cell had no string
                    Log.w("DataRepository", "Could not find 'values' array, or cell data was not as expected for $sheetName!$cellRange in API response. Response: $apiResponseJsonString")
                    return null
                } catch (e: JSONException) {
                    // This catches errors parsing the Google Sheets API's own JSON wrapper
                    Log.e("DataRepository", "Error parsing Google Sheets API wrapper JSON for $sheetName!$cellRange: ${e.message}. Response was: $apiResponseJsonString", e)
                    return null
                }
            } else { // HTTP Response code was not OK
                val errorStreamContent = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error stream available"
                Log.e("DataRepository", "Network error for $sheetName!$cellRange: $responseCode - ${connection.responseMessage}. Error details: $errorStreamContent")
                return null
            }
        } catch (e: IOException) {
            Log.e("DataRepository", "IOException during network fetch for $sheetName!$cellRange: ${e.message}", e)
            return null
        } catch (e: IllegalArgumentException) {
            // Catches issues like malformed URL (e.g., space in sheetName not URL encoded)
            Log.e("DataRepository", "IllegalArgumentException during network setup for $sheetName!$cellRange (check URL components): ${e.message}", e)
            return null
        } catch (e: Exception) { // Generic catch-all for unexpected issues
            Log.e("DataRepository", "Generic exception during network fetch for $sheetName!$cellRange: ${e.message}", e)
            return null
        }
    }

    fun reloadDataFromFile() {
        Log.d("DataRepository", "Explicitly reloading data from file/cache...")
        // This re-runs loadInitialData which updates LiveData.
        // For alarms, the service would need to call fetchAndRefreshAllDataSources again.
        loadInitialData()
    }

    fun refreshDataFromWorkerCache() {
        CoroutineScope(Dispatchers.IO).launch { // Use IO for parsing consistency
            val data = JsonUpdateWorker.latestRawJsonStringFromSheet
            val parsedJson = if (data != null) {
                try {
                    jsonPullUtil.parseJson(data)
                } catch (e: JSONException) {
                    Log.e("DataRepository", "Failed to parse JSON from worker cache: ${e.message}")
                    null
                }
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                _rawJsonFromSheet.value = data
                _parsedSheetJsonObject.value = parsedJson
                Log.d("DataRepository", "Refreshed LiveData from worker's static cache.")
            }
        }
    }
}