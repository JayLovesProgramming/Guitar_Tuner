package com.example.guitartuner.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class GuitarTunerViewModel : ViewModel() {
    private val _note = MutableLiveData<String>().apply {
        value = "No sound detected"
    }
    val note: LiveData<String> = _note

    private val _frequency = MutableLiveData<Float>().apply {
        value = 0f
    }
    val frequency: LiveData<Float> = _frequency

    private var isListening = false
    private var listeningJob: Job? = null
    private val viewModelScope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val guitarNotes = listOf(
        Pair("E2", 82.41f),
        Pair("A2", 110.00f),
        Pair("D3", 146.83f),
        Pair("G3", 196.00f),
        Pair("B3", 246.94f),
        Pair("E4", 329.63f)
    )

    fun stopListening() {
        Log.d("GuitarTunerViewModel", "Stopping listening")
        isListening = false
        listeningJob?.cancel()
        listeningJob = null
    }


    private fun calculateFrequency(audioBuffer: ShortArray, sampleRate: Int): Float {
        val length = audioBuffer.size
        val real = DoubleArray(length)
        val imag = DoubleArray(length)

        // Perform FFT (Fast Fourier Transform)
        for (i in audioBuffer.indices) {
            real[i] = audioBuffer[i].toDouble()
            imag[i] = 0.0
        }

        fft(real, imag)

        // Find the peak frequency
        var maxMagnitude = -1.0
        var peakIndex = 0
        for (i in real.indices) {
            val magnitude = sqrt(real[i] * real[i] + imag[i] * imag[i])
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude
                peakIndex = i
            }
        }

        return peakIndex * sampleRate / length.toFloat()
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        val m = log10(n.toDouble()).toInt()

        for (i in 1 until n) {
            var j = 0
            for (k in 0 until m) {
                if ((i shr k and 1) != 0) {
                    j = j or (1 shl m - 1 - k)
                }
            }
            if (i < j) {
                val tempReal = real[i]
                val tempImag = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempReal
                imag[j] = tempImag
            }
        }

        var step = 2
        while (step <= n) {
            val delta = (2.0 * Math.PI / step).toFloat()
            val sine = Math.sin(delta / 2.0).toFloat()
            val multiplier = (-2.0 * sine * sine).toFloat()

            for (k in 0 until step / 2) {
                val cosine = Math.cos(delta * k.toDouble()).toFloat()
                val sineK = Math.sin(delta * k.toDouble()).toFloat()

                for (i in k until n step step) {
                    val j = i + step / 2
                    val tempReal = cosine * real[j] - sineK * imag[j]
                    val tempImag = cosine * imag[j] + sineK * real[j]

                    real[j] = real[i] - tempReal
                    imag[j] = imag[i] - tempImag
                    real[i] += tempReal
                    imag[i] += tempImag
                }
            }
            step *= 2
        }
    }

    fun startListening(context: Context) {
        Log.d("GuitarTunerViewModel", "Starting listening")

        // Check microphone permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("GuitarTunerViewModel", "Microphone permission not granted")
            _note.value = "Microphone permission required"
            return
        }

        if (isListening) {
            Log.d("GuitarTunerViewModel", "Already listening")
            return
        }

        try {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            Log.d("GuitarTunerViewModel", "AudioRecord state: ${audioRecord.state}")
            Log.d("GuitarTunerViewModel", "AudioRecord recording state: ${audioRecord.recordingState}")

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("GuitarTunerViewModel", "AudioRecord not initialized")
                _note.value = "Audio initialization failed"
                return
            }

            audioRecord.startRecording()

            isListening = true
            listeningJob = viewModelScope.launch {
                val audioBuffer = ShortArray(bufferSize)
                try {
                    while (isListening) {
                        val readCount = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                        Log.d("GuitarTunerViewModel", "Read count: $readCount")

                        if (readCount > 0) {
                            val frequency = calculateFrequency(audioBuffer, sampleRate)
                            _frequency.postValue(frequency)

                            val closestNote = guitarNotes.minByOrNull { abs(it.second - frequency) }
                            if (closestNote != null && abs(closestNote.second - frequency) < 5) {
                                _note.postValue("Note: ${closestNote.first}")
                            } else {
                                _note.postValue("No guitar note detected")
                            }
                        } else {
                            Log.e("GuitarTunerViewModel", "Error reading audio: $readCount")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GuitarTunerViewModel", "Error in listening loop", e)
                } finally {
                    audioRecord.stop()
                    audioRecord.release()
                    isListening = false
                }
            }
        } catch (e: Exception) {
            Log.e("GuitarTunerViewModel", "Error starting audio recording", e)
            _note.value = "Error: ${e.localizedMessage}"
        }
    }

    // Rest of the code remains the same...
}