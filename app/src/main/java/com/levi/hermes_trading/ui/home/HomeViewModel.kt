package com.levi.hermes_trading.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.levi.hermes_trading.JsonPull
import org.json.JSONObject

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>()
    val text: LiveData<String> = _text

    private val jsonPull = JsonPull()

    init {
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        jsonPull.pullJson { rawJson ->
            val jsonObject = parseJson(rawJson)
            if (jsonObject != null) {
                val prettyString = buildString {
                    for (key in jsonObject.keys()) {
                        val inner = jsonObject.optJSONObject(key)
                        appendLine(key)
                        if (inner != null) {
                            for (innerKey in inner.keys()) {
                                appendLine("    $innerKey = ${inner.get(innerKey)}")
                            }
                        }
                        appendLine()
                    }
                }
                _text.postValue(prettyString)
            } else {
                _text.postValue("Fehler beim Parsen der Daten.")
            }
        }
    }

    private fun parseJson(jsonString: String?): JSONObject? {
        return try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}
