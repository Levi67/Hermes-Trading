package com.levi.hermes_trading // Or your preferred package for workers

import android.content.Context
import android.util.Log // <--- ADD THIS IMPORT
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class JsonUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val jsonPull = JsonPull() // Instantiate your class

    companion object {
        const val JSON_FILENAME = "cached_sheet_data.json"
        // This holds the latest fetched RAW JSON string from the sheet cell
        var latestRawJsonStringFromSheet: String? = null
            set // Allow external read but only internal write from the worker
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) { // Ensure blocking network call is on IO dispatcher
            try {
                // Use the synchronous version of your pullJson
                val rawJsonString = jsonPull.pullJsonSynchronously()

                if (rawJsonString != null) {
                    // Save to in-memory static variable
                    latestRawJsonStringFromSheet = rawJsonString
                    Log.d("JsonUpdateWorker", "Successfully fetched JSON: $rawJsonString")


                    // Save the raw JSON string to internal file
                    saveJsonToFile(applicationContext, rawJsonString)
                    Result.success()
                } else {
                    Log.w("JsonUpdateWorker", "Fetched JSON string is null.")
                    // Handle the case where the cell might be empty or data not found as expected
                    // You might still want to clear the old cache or leave it
                    // For now, let's treat it as a failure if we expect data.
                    Result.failure()
                }
            } catch (e: IOException) {
                Log.e("JsonUpdateWorker", "IOException during JSON fetch or save", e)
                Result.retry() // Or Result.failure() depending on your strategy
            } catch (e: org.json.JSONException) {
                Log.e("JsonUpdateWorker", "JSONException parsing sheet's response", e)
                Result.failure()
            } catch (e: Exception) {
                Log.e("JsonUpdateWorker", "Unexpected error in doWork", e)
                Result.failure()
            }
        }
    }

    private fun saveJsonToFile(context: Context, jsonData: String) {
        try {
            val file = File(context.filesDir, JSON_FILENAME)
            FileOutputStream(file).use {
                it.write(jsonData.toByteArray())
            }
            Log.d("JsonUpdateWorker", "JSON saved to file: $JSON_FILENAME")
        } catch (e: IOException) {
            Log.e("JsonUpdateWorker", "Error saving JSON to file", e)
            // Consider how to handle file writing errors, maybe return a boolean success/failure
        }
    }
}