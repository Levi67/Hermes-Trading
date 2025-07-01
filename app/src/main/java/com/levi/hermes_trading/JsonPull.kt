package com.levi.hermes_trading

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException // Added for clarity

class JsonPull {

    // Modified to be synchronous and throw exceptions for WorkManager to handle
    @Throws(IOException::class, org.json.JSONException::class) // Declare possible exceptions
    fun pullJsonSynchronously(): String? {
        val sheetId = "1YtcG_4Vp8XGMlaza-CkZCoxPC_C57twUCdZzH8gEI9g"
        val apiKey = "AIzaSyCvsSkEFWlAa0Cjk62mW5PfnteYxYbljTo" // IMPORTANT: Consider security for API keys
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/C3?key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${response.code} from API: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body from API")

            val sheetJson = JSONObject(body) // Can throw JSONException
            val valuesArray = sheetJson.optJSONArray("values")
            // Return the raw string from the cell, or null if not found as expected
            return valuesArray?.optJSONArray(0)?.optString(0)
        }
    }

    // parseJson remains the same or you might not need it if the worker stores the raw string
    fun parseJson(jsonString: String?): JSONObject? {
        return try {
            if (jsonString == null) null else JSONObject(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}