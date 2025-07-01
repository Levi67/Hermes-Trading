package com.levi.hermes_trading.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.levi.hermes_trading.JsonPull
import com.levi.hermes_trading.model.EtfEntry
import org.json.JSONObject

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>()
    val text: LiveData<String> = _text

    private val _items = MutableLiveData<List<EtfEntry>>()
    val items: LiveData<List<EtfEntry>> = _items

    private val jsonPull = JsonPull()

    init {
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        jsonPull.pullJson { rawJson ->
            val resultList = mutableListOf<EtfEntry>()
            val jsonObject = parseJson(rawJson)

            if (jsonObject != null) {
                val prettyString = buildString {
                    for (key in jsonObject.keys()) {
                        val inner = jsonObject.optJSONObject(key)
                        appendLine(key)
                        if (inner != null) {
                            val value = inner.optString("value", "")
                            val urgency = inner.optString("urgency", "")
                            val color = inner.optString("color", "#000000")
                            appendLine("    value = $value")
                            appendLine("    urgency = $urgency")
                            appendLine("    color = $color")

                            // Add to list
                            resultList.add(EtfEntry(key, value, urgency, color))
                        }
                        appendLine()
                    }
                }
                _text.postValue(prettyString)
            } else {
                _text.postValue("Fehler beim Parsen der Daten.")
            }

            _items.postValue(resultList)
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
