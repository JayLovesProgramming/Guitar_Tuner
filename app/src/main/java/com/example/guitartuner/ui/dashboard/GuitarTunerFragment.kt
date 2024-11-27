package com.example.guitartuner.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.guitartuner.databinding.FragmentDashboardBinding

class GuitarTunerFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: GuitarTunerViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                viewModel.startListening(requireContext())
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error starting listening", e)
                binding.noteTextView.text = "Error: ${e.localizedMessage}"
            }
        } else {
            binding.noteTextView.text = "Microphone permission denied"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[GuitarTunerViewModel::class.java]

        viewModel.note.observe(viewLifecycleOwner) { note ->
            binding.noteTextView.text = note
        }

        viewModel.frequency.observe(viewLifecycleOwner) { frequency ->
            binding.frequencyTextView.text = String.format("%.2f Hz", frequency)
        }

        binding.startButton.setOnClickListener {
            requestMicrophonePermission()
        }

        binding.stopButton.setOnClickListener {
            viewModel.stopListening()
        }

        return binding.root
    }

    private fun requestMicrophonePermission() {
        when {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                try {
                    viewModel.startListening(requireContext())
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Error starting listening", e)
                    binding.noteTextView.text = "Error: ${e.localizedMessage}"
                }
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                binding.noteTextView.text = "Microphone permission is required to detect guitar notes"
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopListening()
        _binding = null
    }
}