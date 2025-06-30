package com.levi.hermes_trading

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class JsonPull {

    // 1. Fetch the raw JSON string from Google Sheets cell C3
    fun pullJson(onResult: (String?) -> Unit) {
        val sheetId = "1YtcG_4Vp8XGMlaza-CkZCoxPC_C57twUCdZzH8gEI9g"
        val apiKey = "AIzaSyCvsSkEFWlAa0Cjk62mW5PfnteYxYbljTo"
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$sheetId/values/C3?key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onResult(null)
                        return@use
                    }
                    val body = response.body?.string()
                    if (body == null) {
                        onResult(null)
                        return@use
                    }

                    val sheetJson = JSONObject(body)
                    val valuesArray = sheetJson.optJSONArray("values")
                    val cellJsonString = valuesArray?.optJSONArray(0)?.optString(0)

                    onResult(cellJsonString)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }.start()
    }

    // 2. Parse the raw JSON string into JSONObject
    fun parseJson(jsonString: String?): JSONObject? {
        return try {
            if (jsonString == null) null else JSONObject(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
