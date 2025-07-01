package com.levi.hermes_trading.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.levi.hermes_trading.databinding.FragmentHomeBinding // Make sure this is your fragment's binding class
import com.levi.hermes_trading.viewmodel.DataViewModelFactory

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var etfAdapter: EtfAdapter // Declare the adapter

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
        etfAdapter = EtfAdapter() // Initialize your adapter
        binding.etfRecyclerView.apply { // Use the ID from your fragment_home.xml
            adapter = etfAdapter
            layoutManager = LinearLayoutManager(context)
        }
        Log.d("HomeFragment", "RecyclerView setup complete.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated: Setting up observers.")
        observeViewModelData()

        // Example: If you have a SwipeRefreshLayout or a button to refresh
        // binding.swipeRefreshLayoutHome.setOnRefreshListener {
        //     homeViewModel.refreshEtfData()
        // }
        // binding.buttonRefreshHome.setOnClickListener {
        //     homeViewModel.refreshEtfData()
        // }
    }

    private fun observeViewModelData() {
        // Observe the list of EtfEntry objects
        homeViewModel.etfEntries.observe(viewLifecycleOwner) { entries ->
            if (entries != null) {
                Log.i("HomeFragment", "ETF entries received: ${entries.size} items")
                etfAdapter.submitList(entries) // Submit the list to the adapter
                // For SwipeRefreshLayout, stop the refreshing animation
                // binding.swipeRefreshLayoutHome.isRefreshing = false
            } else {
                Log.w("HomeFragment", "ETF entries list is null.")
                etfAdapter.submitList(emptyList())
                // binding.swipeRefreshLayoutHome.isRefreshing = false
            }
        }

        // You can still observe rawJsonStringData for debugging if you like
        homeViewModel.rawJsonStringData.observe(viewLifecycleOwner) { jsonString ->
            if (jsonString != null) {
                Log.d("HomeFragment", "Raw JSON for Home debug: $jsonString")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.etfRecyclerView.adapter = null // Clear adapter to prevent memory leaks
        _binding = null
        Log.d("HomeFragment", "onDestroyView called.")
    }
}