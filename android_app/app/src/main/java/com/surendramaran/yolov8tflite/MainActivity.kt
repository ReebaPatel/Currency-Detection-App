package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
//import androidx.activity.result.registerForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrator: Vibrator

    private var isFrontCamera = false
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private lateinit var cameraExecutor: ExecutorService

    private var lastSpokenTime = 0L
    private var lastVibratedTime = 0L
    private val debounceInterval = 5000L // 5 seconds debounce to avoid repeating output
    private val vibrationDebounceInterval = 2000L // 2 seconds debounce for vibrations
    private var isTtsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize vibrator
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)

        // Retrieve selected language from intent or SharedPreferences
        val selectedLanguage = intent.getStringExtra("SELECTED_LANGUAGE")
            ?: sharedPreferences.getString("SelectedLanguage", "en") ?: "en"

        Log.d(TAG, "Selected Language: $selectedLanguage")

        // Save selected language for future sessions
        sharedPreferences.edit().putString("SelectedLanguage", selectedLanguage).apply()

        // Initialize TTS with selected language
        initializeTextToSpeech(selectedLanguage)

        // Initialize the detector
        detector = Detector(baseContext, Constants.MODEL_PATH, Constants.LABELS_PATH, this)
        detector.setup()

        // Check camera permissions and start camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initializeTextToSpeech(languageCode: String) {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = when (languageCode) {
                    "en" -> Locale.US
                    "hi" -> Locale("hi")
                    "mr" -> Locale("mr")
                    "kn" -> Locale("kn")
                    "gu" -> Locale("gu")
                    else -> Locale.US
                }
                setTtsLanguage(locale)
            } else {
                Log.e(TAG, "TTS: Initialization failed. Error code: $status")
                showToast("TTS initialization failed. Please check language support.")
                isTtsInitialized = false
            }
        }
    }
    private fun setTtsLanguage(locale: Locale) {
        val result = textToSpeech.setLanguage(locale)
        when (result) {
            TextToSpeech.LANG_MISSING_DATA -> {
                Log.e(TAG, "TTS: Language data missing for ${locale.displayLanguage}")
                showToast("Please download ${locale.displayLanguage} TTS data in device settings.")
                textToSpeech.setLanguage(Locale.US) // Fallback
                isTtsInitialized = true
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.e(TAG, "TTS: Language not supported: ${locale.displayLanguage}")
                showToast("${locale.displayLanguage} is not supported by TTS.")
                textToSpeech.setLanguage(Locale.US) // Fallback
                isTtsInitialized = true
            }
            else -> {
                // Check and set a voice for the selected language
                val voices = textToSpeech.voices
                val matchingVoice = voices?.find { it.locale == locale && !it.isNetworkConnectionRequired }
                if (matchingVoice != null) {
                    textToSpeech.voice = matchingVoice
                    Log.d(TAG, "TTS: Voice set to ${matchingVoice.name} for ${locale.displayLanguage}")
                } else {
                    Log.w(TAG, "TTS: No suitable voice found for ${locale.displayLanguage}. Using default.")
                    showToast("No voice found for ${locale.displayLanguage}. Install it in settings.")
                }
                isTtsInitialized = true
                Log.d(TAG, "TTS: Language set to ${locale.displayLanguage}")
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            detector.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
        textToSpeech.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.invalidate()

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSpokenTime >= debounceInterval) {
                lastSpokenTime = currentTime
                val message = "No currency detected"
                val translatedMessage = translateText(message)
                showToast(translatedMessage)
                speakOut(translatedMessage) // Pass translated text directly
            }
            clearAppCache()
        }

    }
    private fun translateText(text: String): String {
        val translations = mapOf(
            "en" to mapOf(
                "Detected" to "Detected",
                "10 rupee note" to "10 rupee note",
                "20 rupee note" to "20 rupee note",
                "50 rupee note" to "50 rupee note",
                "100 rupee note" to "100 rupee note",
                "200 rupee note" to "200 rupee note",
                "500 rupee note" to "500 rupee note",
                "2000 rupee note" to "2000 rupee note",
                "rupees" to "rupees"
            ),
            "hi" to mapOf( // Hindi Translations
                "Detected" to "पता चला",
                "10 rupee note" to "10 रुपये का नोट",
                "20 rupee note" to "20 रुपये का नोट",
                "50 rupee note" to "50 रुपये का नोट",
                "100 rupee note" to "100 रुपये का नोट",
                "200 rupee note" to "200 रुपये का नोट",
                "500 rupee note" to "500 रुपये का नोट",
                "2000 rupee note" to "2000 रुपये का नोट",
                "rupees" to "रुपये",
                "No Currency Detected" to "कोई पैसा नहीं पाया गया।"

            ),
            "mr" to mapOf( // Marathi Translations
                "Detected" to "आढळले",
                "10 rupee note" to "10 रुपयांची नोट",
                "20 rupee note" to "20 रुपयांची नोट",
                "50 rupee note" to "50 रुपयांची नोट",
                "100 rupee note" to "100 रुपयांची नोट",
                "200 rupee note" to "200 रुपयांची नोट",
                "500 rupee note" to "500 रुपयांची नोट",
                "2000 rupee note" to "2000 रुपयांची नोट",
                "rupees" to "रुपये",
                "No Currency Detected" to "कोणतेही पैसे सापडले नाहीत"
            ),
            "kn" to mapOf( // Kannada Translations
                "Detected" to "ಗೊತ್ತಾಗಿದೆ",
                "10 rupee note" to "10 ರೂಪಾಯಿ ನೋಟು",
                "20 rupee note" to "20 ರೂಪಾಯಿ ನೋಟು",
                "50 rupee note" to "50 ರೂಪಾಯಿ ನೋಟು",
                "100 rupee note" to "100 ರೂಪಾಯಿ ನೋಟು",
                "200 rupee note" to "200 ರೂಪಾಯಿ ನೋಟು",
                "500 rupee note" to "500 ರೂಪಾಯಿ ನೋಟು",
                "2000 rupee note" to "2000 ರೂಪಾಯಿ ನೋಟು",
                "rupees" to "ರೂಪಾಯಿಗಳು",
                "No Currency Detected" to "ಯಾವುದೇ ಹಣ ಕಂಡುಬಂದಿಲ್ಲ"
            ),
            "gu" to mapOf( // Gujarati Translations
                "Detected" to "શોધાયું",
                "10 rupee note" to "10 રૂપિયાનો નોટ",
                "20 rupee note" to "20 રૂપિયાનો નોટ",
                "50 rupee note" to "50 રૂપિયાનો નોટ",
                "100 rupee note" to "100 રૂપિયાનો નોટ",
                "200 rupee note" to "200 રૂપિયાનો નોટ",
                "500 rupee note" to "500 રૂપિયાનો નોટ",
                "2000 rupee note" to "2000 રૂપિયાનો નોટ",
                "rupees" to "રૂપિયા",
                "No Currency Detected" to "કોઈ પૈસા મળ્યા નથી"
            )
        )

        val selectedLang = sharedPreferences.getString("SelectedLanguage", "en") ?: "en"
        val langTranslations = translations[selectedLang] ?: translations["en"]!!

        // Translate detected words
        return text.split(", ").joinToString(", ") { word ->
            langTranslations[word] ?: word  // If not found, keep original
        }
    }

    private fun triggerHapticFeedback(detectedNotes: String) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator available on this device")
            return
        }

        // Create different vibration patterns for each note
        val pattern = when {
            detectedNotes.contains("10") -> longArrayOf(0, 200) // Single short vibration for 10
            detectedNotes.contains("20") -> longArrayOf(0, 100, 100, 100) // Two quick vibrations for 20
            detectedNotes.contains("50") -> longArrayOf(0, 300) // Single longer vibration for 50
            detectedNotes.contains("100") -> longArrayOf(0, 100, 100, 100, 100, 100) // Three quick vibrations for 100
            detectedNotes.contains("200") -> longArrayOf(0, 200, 100, 200) // Two medium vibrations for 200
            detectedNotes.contains("500") -> longArrayOf(0, 100, 50, 100, 50, 100) // Three very quick vibrations for 500
            detectedNotes.contains("2000") -> longArrayOf(0, 500) // One long vibration for 2000
            else -> null
        }

        pattern?.let {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val vibrationEffect = VibrationEffect.createWaveform(it, -1)
                    vibrator.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(it, -1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering haptic feedback", e)
            }
        }
    }

override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
    runOnUiThread {
        binding.overlay.apply {
            setResults(boundingBoxes)
            invalidate()
        }

        val detectedNotes = boundingBoxes.joinToString(", ") { it.clsName }

        if (detectedNotes.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()

            // Speech debounce
            if (currentTime - lastSpokenTime >= debounceInterval) {
                lastSpokenTime = currentTime
                val message = "$detectedNotes rupees"
                val translatedMessage = translateText(message)
                speakOut(translatedMessage)
            }

            // ⭐️ Add this block for vibration ⭐️
            if (currentTime - lastVibratedTime >= vibrationDebounceInterval) {
                lastVibratedTime = currentTime
                triggerHapticFeedback(detectedNotes)
            }
        }
        clearAppCache();
    }
}
//        }
//    }

    private fun speakOut(alreadyTranslatedText: String) {
        Log.d(TAG, "Attempting to speak: $alreadyTranslatedText in language: ${textToSpeech.language.displayLanguage}")
        if (!isTtsInitialized) {
            Log.e(TAG, "TTS: Engine not initialized.")
            return
        }
        textToSpeech.speak(alreadyTranslatedText, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun clearAppCache() {
        try {
            // Clear internal cache
            val cacheDir = cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }

            // Clear external cache (if available)
            val externalCacheDir = externalCacheDir
            if (externalCacheDir?.exists() == true && externalCacheDir.isDirectory) {
                externalCacheDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }

            Log.d(TAG, "App cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}")
        }
    }
}