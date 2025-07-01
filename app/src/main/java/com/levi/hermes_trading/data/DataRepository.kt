package com.levi.hermes_trading.data // Or com.levi.hermes_trading.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.levi.hermes_trading.JsonPull
import com.levi.hermes_trading.JsonUpdateWorker
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

    // LiveData to hold the raw JSON string from the sheet cell
    private val _rawJsonFromSheet = MutableLiveData<String?>()
    val rawJsonFromSheet: LiveData<String?> = _rawJsonFromSheet

    // LiveData to hold the parsed JSONObject
    private val _parsedSheetJsonObject = MutableLiveData<JSONObject?>()
    val parsedSheetJsonObject: LiveData<JSONObject?> = _parsedSheetJsonObject

    private val jsonPullUtil = JsonPull() // Your utility for parsing

    init {
        Log.d("DataRepository", "Initializing and loading initial data...")
        loadInitialData()
    }

    private fun loadInitialData() {
        CoroutineScope(Dispatchers.IO).launch {
            var dataToLoad = JsonUpdateWorker.latestRawJsonStringFromSheet // Check in-memory first (from worker)
            var source = "In-memory cache (Worker)"

            if (dataToLoad == null) {
                Log.d("DataRepository", "Worker cache miss, attempting to load from file.")
                dataToLoad = loadJsonStringFromFile()
                source = "File storage"
            }

            if (dataToLoad != null) {
                Log.d("DataRepository", "Data loaded from $source for initial load.")
                // Update worker's cache if loaded from file and worker's cache was null
                if (JsonUpdateWorker.latestRawJsonStringFromSheet == null) {
                    JsonUpdateWorker.latestRawJsonStringFromSheet = dataToLoad
                }
            } else {
                Log.d("DataRepository", "No data found in worker cache or file for initial load.")
            }

            withContext(Dispatchers.Main) {
                _rawJsonFromSheet.value = dataToLoad
                _parsedSheetJsonObject.value = jsonPullUtil.parseJson(dataToLoad)
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
            withContext(Dispatchers.IO) {
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
     * updates the in-memory cache (JsonUpdateWorker.latestRawJsonStringFromSheet),
     * and updates the LiveData instances.
     * This is intended to be called by the foreground service or any explicit refresh action.
     *
     * @return true if the entire process was successful, false otherwise.
     */
    suspend fun fetchAndRefreshAllDataSources(): Boolean {
        Log.i("DataRepository", "Attempting to fetch and refresh all data sources...")
        return withContext(Dispatchers.IO) { // Perform all operations on IO thread
            val fetchedJsonString = fetchJsonStringFromNetwork() // This will be the direct content of cell C3

            if (fetchedJsonString != null) {
                Log.d("DataRepository", "Successfully fetched new JSON data from network (content of C3).")
                val saveSuccess = saveJsonStringToFile(fetchedJsonString)
                if (saveSuccess) {
                    JsonUpdateWorker.latestRawJsonStringFromSheet = fetchedJsonString
                    Log.d("DataRepository", "Updated JsonUpdateWorker's static cache.")

                    withContext(Dispatchers.Main) {
                        _rawJsonFromSheet.value = fetchedJsonString
                        // Assuming C3 contains the full JSON string your JsonPull utility needs
                        _parsedSheetJsonObject.value = jsonPullUtil.parseJson(fetchedJsonString)
                        Log.i("DataRepository", "LiveData updated with new data from network.")
                    }
                    true // Overall success
                } else {
                    Log.e("DataRepository", "Failed to save fetched JSON to file.")
                    false // Failed to save
                }
            } else {
                Log.w("DataRepository", "Network fetch returned null or failed (cell C3 content).")
                false // Network fetch failed
            }
        }
    }

    /**
     * Fetches the content of a specific cell from Google Sheets using Sheets API v4.
     * Parses the API response to extract the direct cell value.
     * The cell value itself is assumed to be the JSON string needed by the app.
     */
    private suspend fun fetchJsonStringFromNetwork(): String? {
        // --- Configuration Variables ---
        val sheetId = "1YtcG_4Vp8XGMlaza-CkZCoxPC_C57twUCdZzH8gEI9g"
        // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
        // CRITICAL: Ensure 'sheetName' matches the EXACT tab name in your Google Sheet.
        // This is a very common point of error. It is case-sensitive.
        val sheetName = "Sheet1" // <<< VERIFY AND CHANGE IF YOUR SHEET TAB NAME IS DIFFERENT!
        // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
        val cellRange = "C3"

        // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
        // CRITICAL SECURITY WARNING: This API key is hardcoded and has been shared.
        // 1. You MUST regenerate this API key in your Google Cloud Console IMMEDIATELY.
        // 2. Replace the key below with your NEW, REGENERATED key.
        // 3. Restrict the NEW key (Application restrictions: Android app, API restrictions: Google Sheets API).
        // 4. For production, use secure methods to store API keys (e.g., local.properties + BuildConfig).
        val apiKey = "AIzaSyCvsSkEFWlAa0Cjk62mW5PfnteYxYbljTo" // <<< REPLACE WITH YOUR NEW, REGENERATED KEY
        // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---

        // --- Construct the URL ---
        val baseUrl = "https://sheets.googleapis.com/v4/spreadsheets"
        val urlString = "$baseUrl/$sheetId/values/$sheetName!$cellRange?key=$apiKey"

        // --- Validation Check for Configuration ---
        if (sheetId.isBlank() || sheetName.isBlank() || cellRange.isBlank() || apiKey.isBlank()) {
            Log.e("DataRepository", "Network configuration is incomplete (SheetID, SheetName, CellRange, or APIKey is blank). Cannot fetch.")
            return null
        }
        // Optional: A more specific check if the apiKey still looks like a common placeholder you might use
        // This is just an example, adjust if you use a specific placeholder text
        if (apiKey == "YOUR_DEFAULT_PLACEHOLDER_API_KEY_TEXT") {
            Log.e("DataRepository", "API Key appears to be an unconfigured placeholder.")
            return null
        }

        Log.i("DataRepository", "Fetching from URL: $urlString")

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000 // 15 seconds
                connection.readTimeout = 15000   // 15 seconds

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val apiResponseJsonString = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("DataRepository", "Successfully fetched from network. Raw API Response: $apiResponseJsonString")

                    if (apiResponseJsonString.isNotBlank()) {
                        // Parse the Google Sheets API response to extract the cell's actual content
                        try {
                            val jsonApiResponse = JSONObject(apiResponseJsonString)
                            val valuesArray = jsonApiResponse.optJSONArray("values")
                            if (valuesArray != null && valuesArray.length() > 0) {
                                val firstRowArray = valuesArray.optJSONArray(0)
                                if (firstRowArray != null && firstRowArray.length() > 0) {
                                    // This is the actual string content of cell C3
                                    val cellContentString = firstRowArray.optString(0, null)
                                    if (cellContentString != null) {
                                        Log.d("DataRepository", "Extracted cell C3 content: $cellContentString")
                                        // Now, we assume cellContentString is the JSON your app needs
                                        // Further validation can be done here if needed (e.g., try parsing with JSONObject)
                                        try {
                                            JSONObject(cellContentString) // Validate if the cell content itself is valid JSON
                                            return@withContext cellContentString // Return the cell content
                                        } catch (e: JSONException) {
                                            Log.e("DataRepository", "Content of cell C3 ('$cellContentString') is not valid JSON: ${e.message}")
                                            return@withContext null // Cell content wasn't the expected JSON
                                        }
                                    } else {
                                        Log.w("DataRepository", "Cell C3 ($sheetName!$cellRange) has no value or is not a string in the API response.")
                                        return@withContext null
                                    }
                                }
                            }
                            Log.w("DataRepository", "Could not find 'values' array or cell data for $sheetName!$cellRange in API response. Response: $apiResponseJsonString")
                            return@withContext null
                        } catch (e: JSONException) {
                            Log.e("DataRepository", "Error parsing Google Sheets API wrapper JSON: ${e.message}. Response was: $apiResponseJsonString", e)
                            return@withContext null
                        }
                    } else {
                        Log.w("DataRepository", "Network response (API wrapper) was empty for $sheetName!$cellRange.")
                        return@withContext null
                    }
                } else {
                    val errorStreamContent = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error stream available"
                    Log.e("DataRepository", "Network error for $sheetName!$cellRange: $responseCode - ${connection.responseMessage}. Error details: $errorStreamContent")
                    return@withContext null
                }
            } catch (e: IOException) {
                Log.e("DataRepository", "IOException during network fetch for $sheetName!$cellRange: ${e.message}", e)
                return@withContext null
            } catch (e: IllegalArgumentException) { // Catch issues like malformed URL (e.g. space in sheetName)
                Log.e("DataRepository", "IllegalArgumentException during network setup (check URL components like sheetName): ${e.message}", e)
                return@withContext null
            } catch (e: Exception) { // Generic catch-all
                Log.e("DataRepository", "Generic exception during network fetch for $sheetName!$cellRange: ${e.message}", e)
                return@withContext null
            }
        }
    }

    /**
     * Call this method to explicitly tell the repository to re-evaluate its data sources
     * and update its LiveData. This is useful if you know the worker has just
     * updated the file/cache and you want the UI to reflect changes immediately.
     */
    fun reloadDataFromFile() {
        Log.d("DataRepository", "Explicitly reloading data from file/cache...")
        loadInitialData()
    }

    /**
     *  Refreshes LiveData directly from the Worker's static cache.
     *  This is quicker if you are certain the worker has just run and updated its cache.
     */
    fun refreshDataFromWorkerCache() {
        CoroutineScope(Dispatchers.Main).launch {
            val data = JsonUpdateWorker.latestRawJsonStringFromSheet
            _rawJsonFromSheet.value = data
            _parsedSheetJsonObject.value = jsonPullUtil.parseJson(data)
            Log.d("DataRepository", "Refreshed LiveData from worker's static cache.")
        }
    }
}