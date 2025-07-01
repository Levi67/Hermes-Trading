package com.levi.hermes_trading.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.levi.hermes_trading.databinding.FragmentSettingsBinding // Import ViewBinding class

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsViewModel.autoSyncEnabled.observe(viewLifecycleOwner) { isEnabled ->
            Log.d("SettingsFragment", "Observed autoSyncEnabled: $isEnabled, updating switch.")
            // Prevent recursive listener trigger by checking if the switch state is already correct
            if (binding.switchAutoMinuteSync.isChecked != isEnabled) {
                binding.switchAutoMinuteSync.isChecked = isEnabled
            }
        }

        binding.switchAutoMinuteSync.setOnCheckedChangeListener { _, isChecked ->
            Log.d("SettingsFragment", "Switch toggled by user: $isChecked")
            // Only save if the change is from the user, not from the observer update
            if (settingsViewModel.autoSyncEnabled.value != isChecked) {
                settingsViewModel.saveAutoSyncSetting(isChecked)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding reference
    }
}