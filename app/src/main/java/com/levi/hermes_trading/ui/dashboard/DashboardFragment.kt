package com.levi.hermes_trading.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer // Not strictly necessary if using the lambda syntax for observe
import androidx.lifecycle.ViewModelProvider
import com.levi.hermes_trading.databinding.FragmentDashboardBinding // Make sure this matches your layout file name
import com.levi.hermes_trading.viewmodel.DataViewModelFactory

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val factory = DataViewModelFactory(requireActivity().application)
        dashboardViewModel = ViewModelProvider(this, factory).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        Log.d("DashboardFragment", "onCreateView: ViewModel initialized: $dashboardViewModel")
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("DashboardFragment", "onViewCreated: Setting up observers.")
        observeViewModelData()

        // Call any initial logic from your ViewModel if needed
        dashboardViewModel.someDashboardSpecificLogic()

        // Example: If you wanted a button to trigger a refresh (you'd add the button to XML)
        // binding.buttonRefreshDashboard?.setOnClickListener {
        //     dashboardViewModel.refreshDisplayedData()
        // }
    }

    private fun observeViewModelData() {
        // Observe the formatted display text from the ViewModel
        dashboardViewModel.dashboardDisplayText.observe(viewLifecycleOwner) { displayText ->
            Log.i("DashboardFragment", "Dashboard display text received: $displayText")
            binding.textDashboard.text = displayText // Update the TextView with id 'text_dashboard'
        }

        // You can also observe the raw JSON string if you want it for other purposes or debugging
        dashboardViewModel.rawJsonStringData.observe(viewLifecycleOwner) { rawJson ->
            if (rawJson != null) {
                Log.d("DashboardFragment", "Raw JSON for reference: $rawJson")
                // Potentially display this in another TextView if you add one
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d("DashboardFragment", "onDestroyView called.")
    }
}