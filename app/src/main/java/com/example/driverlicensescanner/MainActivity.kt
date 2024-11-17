package com.example.driverlicensescanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.driverlicensescanner.ui.theme.DriverLicenseScannerTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.hardware.camera2.*
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var surface: Surface
    private lateinit var textureView: TextureView
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var licenseInfoState = mutableStateOf("Scanning for license...")

    // Define the valid keyword "License No" for scanning the driver's license
    private val validLicenseKeyword = "License No"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            DriverLicenseScannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Box(modifier = Modifier.weight(1f)) {
                            CameraPreviewWithScannerOverlay()
                        }
                        LicenseInfoText(modifier = Modifier.padding(16.dp), licenseInfo = licenseInfoState.value)
                    }
                }
            }
        }

        // Request camera permission on startup
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0] // Use the first camera (usually the rear camera)

        try {
            // Open the camera asynchronously
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraError", "Camera opening failed with error code: $error")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e("CameraError", "Error opening camera: ${e.message}")
        }
    }

    private fun createCameraPreviewSession() {
        val texture = textureView.surfaceTexture
        if (texture != null) {
            texture.setDefaultBufferSize(textureView.width, textureView.height)
        }
        surface = Surface(texture)

        try {
            // Create a capture request builder for the camera preview
            val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            // Create a camera capture session for the preview
            cameraDevice.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return

                        cameraCaptureSession = session
                        try {
                            // Start the preview session
                            cameraCaptureSession.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null, null
                            )
                        } catch (e: CameraAccessException) {
                            Log.e("CameraError", "Error starting preview: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("CameraError", "Camera configuration failed")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.e("CameraError", "Error creating capture session: ${e.message}")
        }
    }

    private fun startImageAnalysis() {
        // For this demo, we'll assume that the preview frames are being captured correctly.
        // In a real app, you would extract frames from the Camera2 preview stream and pass them to the OCR processor.

        // Once an image is captured, process it using ML Kit
        val mediaImage = textureView.bitmap // For demo purposes; use actual camera frame here
        if (mediaImage != null) {
            val image = InputImage.fromBitmap(mediaImage, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d("LicenseScanner", "License Detected: ${visionText.text}")

                    if (visionText.text.isNotEmpty()) {
                        if (visionText.text.contains(validLicenseKeyword, ignoreCase = true)) {
                            val extractedInfo = extractDriverLicenseInfo(visionText.text)
                            licenseInfoState.value = extractedInfo
                        } else {
                            licenseInfoState.value = "No valid license content detected"
                        }
                    } else {
                        licenseInfoState.value = "No license detected"
                    }
                }
                .addOnFailureListener {
                    Log.e("LicenseScanner", "License recognition failed", it)
                    licenseInfoState.value = "Error scanning license"
                }
        }
    }

    private fun extractDriverLicenseInfo(text: String): String {
        Log.d("LicenseScanner", "Raw OCR text: $text")

        // Capture Last Name, First Name, Middle Name (flexible spacing and commas)
        val namePattern = Regex("Last Name,\\s*First Name,\\s*Middle Name\\s*(\\S+),\\s*(\\S+)\\s+(\\S+)")
        val nameMatchResult = namePattern.find(text)
        val lastName = nameMatchResult?.groupValues?.get(1) ?: "Not Found"
        val firstName = nameMatchResult?.groupValues?.get(2) ?: "Not Found"
        val middleName = nameMatchResult?.groupValues?.get(3) ?: "Not Found"

        // Capture Nationality and Sex (with modifications for Sex and Nationality)
        val infoPattern = Regex("Nationality[\\s,]*Sex[\\s,]*(.*?)(?=\\n|\\r|\\s+Address)", RegexOption.DOT_MATCHES_ALL)
        val infoMatchResult = infoPattern.find(text)
        val infoFields = infoMatchResult?.groupValues?.get(1)?.split("\\s{2,}".toRegex()) ?: listOf("Not Found", "Not Found")

        // Extract and validate Nationality (first 3 characters)
        val nationality = infoFields.getOrElse(0) { "Not Found" }.trim().take(3)

        // Extract Sex and validate with regex (only "F" or "M")
        val sex = infoFields.getOrElse(1) { "Not Found" }.trim()
        val validatedSex = if (sex.matches(Regex("^[A-Z]{1}$"))) sex else "Not Found"

        // Capture Address (multiline and flexible handling)
        val addressPattern = Regex("Address:\\s*(.*?)(?=\\n|\\r|\\s+License No.|$)", RegexOption.DOT_MATCHES_ALL)
        val addressMatchResult = addressPattern.find(text)
        val address = addressMatchResult?.groupValues?.get(1)?.trim() ?: "Not Found"

        // Capture License No.
        val licenseNoPattern = Regex("License No\\.\\s*(\\S+)")
        val licenseNoMatchResult = licenseNoPattern.find(text)
        val licenseNo = licenseNoMatchResult?.groupValues?.get(1) ?: "Not Found"

        return """
        Last Name: $lastName
        First Name: $firstName
        Middle Name: $middleName
        Nationality: $nationality
        Sex: $validatedSex
        Address: $address
        License No: $licenseNo
    """.trimIndent()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice.close()
        cameraExecutor.shutdown()
    }

    @Composable
    fun CameraPreviewWithScannerOverlay() {
        Box(modifier = Modifier.fillMaxSize()) {
            // Use AndroidView to integrate TextureView with Compose
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TextureView(context).apply {
                        textureView = this
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                // Here, you can create and bind the camera session to this surface.
                                // Example: set up the Camera2 API or CameraX.
                                val surface = Surface(surfaceTexture)
                                startCameraPreview(surface)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                // Handle surface size change if necessary
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                // Handle when the surface is destroyed
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                // Handle surface updates (if needed)
                            }
                        }
                    }
                }
            )

            // Your scanner overlay can go here
            ScannerOverlay()
        }
    }


    @Composable
    fun ScannerOverlay() {
        // Overlay for scanning or drawing a scanning line
    }

    @Composable
    fun LicenseInfoText(modifier: Modifier = Modifier, licenseInfo: String) {
        Column(modifier = modifier) {
            Text(
                text = licenseInfo,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}