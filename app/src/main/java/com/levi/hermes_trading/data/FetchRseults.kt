package com.levi.hermes_trading.data // Or your repository package

/**
 * Data class to hold the result of the data fetch operation.
 * It indicates if the fetch was successful and lists any items that have active alarms.
 */
data class FetchResult(
    val isSuccess: Boolean,
    val activeAlarmItems: List<String> = emptyList() // Names of items with active alarms
) {
    /**
     * Convenience property to quickly check if any alarms are active.
     */
    val hasActiveAlarms: Boolean
        get() = activeAlarmItems.isNotEmpty()
}
