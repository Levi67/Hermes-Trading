package com.levi.hermes_trading.ui.home

import android.content.BroadcastReceiver // <-- Add
import android.content.Context // <-- Add
import android.content.Intent // <-- Add
import android.content.IntentFilter // <-- Add
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.observe
import androidx.localbroadcastmanager.content.LocalBroadcastManager // <-- Add
import androidx.recyclerview.widget.LinearLayoutManager
import com.levi.hermes_trading.databinding.FragmentHomeBinding
import com.levi.hermes_trading.services.DataUpdateForegroundService // <-- Add, for ACTION_DATA_UPDATED
import com.levi.hermes_trading.viewmodel.DataViewModelFactory

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var etfAdapter: EtfAdapter

    // --- Add BroadcastReceiver ---
    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataUpdateForegroundService.ACTION_DATA_UPDATED) {
                Log.d("HomeFragment", "Received ACTION_DATA_UPDATED broadcast. Telling ViewModel to refresh data.")
                // Tell the ViewModel to ask the repository to reload its data.
                // The LiveData observers will then pick up the changes.
                homeViewModel.refreshEtfData()
            }
        }
    }
    // --- End BroadcastReceiver ---

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val factory = DataViewModelFactory(requireActivity().application)
        homeViewModel = ViewModelProvider(this, factory).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()

        Log.d("HomeFragment", "onCreateView: ViewModel initialized: $homeViewModel")
        return root
    }

    private fun setupRecyclerView() {
        etfAdapter = EtfAdapter()
        binding.etfRecyclerView.apply {
            adapter = etfAdapter
            layoutManager = LinearLayoutManager(context)
        }
        Log.d("HomeFragment", "RecyclerView setup complete.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated: Setting up observers.")
        observeViewModelData()

        // You can still keep manual refresh options if you want
        // binding.swipeRefreshLayoutHome.setOnRefreshListener {
        //     homeViewModel.refreshEtfData()
        // }
    }

    private fun observeViewModelData() {
        homeViewModel.etfEntries.observe(viewLifecycleOwner) { entries ->
            if (entries != null) {
                Log.i("HomeFragment", "ETF entries observer: Received ${entries.size} items for UI.")
                etfAdapter.submitList(entries)
            } else {
                Log.w("HomeFragment", "ETF entries observer: Received null list.")
                etfAdapter.submitList(emptyList())
            }
        }

        homeViewModel.rawJsonStringData.observe(viewLifecycleOwner) { jsonString ->
            if (jsonString != null) {
                Log.d("HomeFragment", "Raw JSON for Home debug: $jsonString")
            }
        }
    }

    // --- Add onResume and onPause for BroadcastReceiver lifecycle ---
    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            dataUpdateReceiver, IntentFilter(DataUpdateForegroundService.ACTION_DATA_UPDATED)
        )
        Log.d("HomeFragment", "DataUpdateReceiver registered.")
        // Optionally, trigger an initial refresh when the fragment becomes visible,
        // if the data might have changed while it was paused and you want the latest immediately.
        // homeViewModel.refreshEtfData() // Consider if this is needed or if service handles initial load well
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(dataUpdateReceiver)
        Log.d("HomeFragment", "DataUpdateReceiver unregistered.")
    }
    // --- End onResume/onPause ---

    override fun onDestroyView() {
        super.onDestroyView()
        binding.etfRecyclerView.adapter = null
        _binding = null
        Log.d("HomeFragment", "onDestroyView called.")
    }
}