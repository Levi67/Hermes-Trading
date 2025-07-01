package com.levi.hermes_trading.ui.settings

// REMOVE these imports as they are not needed for PreferenceFragmentCompat
// import android.content.SharedPreferences
// import android.util.Log
// import android.view.LayoutInflater
// import android.view.View
// import android.view.ViewGroup
// import androidx.fragment.app.viewModels
// import androidx.preference.PreferenceManager
// import com.levi.hermes_trading.databinding.FragmentSettingsBinding

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.levi.hermes_trading.R // Make sure this R file import is correct

// Change the base class from Fragment to PreferenceFragmentCompat
class SettingsFragment : PreferenceFragmentCompat() {

    // The _binding and binding properties are NO LONGER NEEDED because
    // PreferenceFragmentCompat handles view creation from the XML.
    // private var _binding: FragmentSettingsBinding? = null
    // private val binding get() = _binding!!

    // SharedPreferences are handled automatically by PreferenceFragmentCompat
    // when interacting with Preference objects linked by app:key.
    // private lateinit var sharedPreferences: SharedPreferences

    // ViewModel is generally not directly used for simple preference saving/loading
    // with PreferenceFragmentCompat, though it can be used for more complex logic.
    // private val settingsViewModel: SettingsViewModel by viewModels()

    // The preference keys can still be useful if you need to access
    // these SharedPreferences values from other parts of your app (like your Service).
    companion object {
        const val PREF_KEY_AUTO_SYNC_ENABLED = "pref_auto_minute_sync_enabled"
        const val PREF_KEY_ALARMS_ENABLED = "pref_key_alarms_enabled"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // This is the core method for PreferenceFragmentCompat.
        // It tells the fragment to build its UI from your preferences.xml file.
        // Make sure "preferences" matches the filename of your XML file in res/xml/.
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // You generally DO NOT override onCreateView, onViewCreated, loadPreferences,
        // or setupListeners in a simple PreferenceFragmentCompat because:
        // 1. View inflation is handled by setPreferencesFromResource.
        // 2. Preference objects (like SwitchPreferenceCompat) automatically save
        //    their state to SharedPreferences when their value changes if they have an app:key.
        // 3. Their initial state is also automatically loaded from SharedPreferences.
    }

    // onCreateView is NOT typically overridden in PreferenceFragmentCompat
    // override fun onCreateView(...) { ... }

    // onViewCreated is NOT typically overridden for basic preferences
    // override fun onViewCreated(...) { ... }

    // loadPreferences() is NOT needed as PreferenceFragmentCompat handles it
    // private fun loadPreferences() { ... }

    // setupListeners() is NOT needed for standard preferences as they handle their own state saving
    // private fun setupListeners() { ... }

    // onDestroyView is still fine to have if you were doing other specific view cleanup,
    // but with no _binding, it's less critical for this simple version.
    // The super.onDestroyView() will be called regardless.
    // override fun onDestroyView() {
    //     super.onDestroyView()
    //     // _binding = null // _binding is removed
    // }
}