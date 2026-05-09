package com.example.cradetv6

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.example.cradetv6.data.AppDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.math.sqrt

class MonitoringService : Service(), SensorEventListener {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var sensorManager: SensorManager
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var toneGenerator: ToneGenerator? = null

    private val NOTIFICATION_ID = 123
    private val CHANNEL_ID = "CraDetMonitoring"

    companion object {
        const val WATCH_NAME = "FB BGS003"
        const val WATCH_MAC = "56:75:DE:1D:5C:2B"
        
        val isMonitoring = MutableStateFlow(false)
        val watchConnected = MutableStateFlow(false)
        val watchRssi = MutableStateFlow(-60)
        val accidentDetected = MutableStateFlow(false)
        val countdownActive = MutableStateFlow(false)
        val countdownValue = MutableStateFlow(20)
        
        // Sensor Data
        val accelX = MutableStateFlow(0f)
        val accelY = MutableStateFlow(0f)
        val accelZ = MutableStateFlow(0f)
        val gForce = MutableStateFlow(0f)
        val gyroX = MutableStateFlow(0f)
        val gyroY = MutableStateFlow(0f)
        val gyroZ = MutableStateFlow(0f)

        // Simulated Vitals
        val heartRate = MutableStateFlow(72)
        val bloodPressure = MutableStateFlow("120/80")
        val isSimulationOn = MutableStateFlow(false)
        val lastDetectedText = MutableStateFlow("")
        val currentLocation = MutableStateFlow<Location?>(null)
        val manualSimulationTrigger = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            e.printStackTrace()
        }

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        startMonitoring()
        startBluetoothMonitoring()
        startVitalsSimulation()
        startLocationUpdates()
        initSpeechRecognizer()
    }

    private fun startLocationUpdates() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { loc ->
                                currentLocation.value = loc
                            }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000) // Update location every 5 seconds
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    serviceScope.launch {
                        delay(2000)
                        if (isMonitoring.value) speechRecognizer?.startListening(intent)
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null) {
                        for (match in matches) {
                            val text = match.lowercase()
                            lastDetectedText.value = text
                            if (text.contains("help")) {
                                triggerAccidentDetection()
                                break
                            }
                        }
                    }
                    if (isMonitoring.value) speechRecognizer?.startListening(intent)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val text = matches[0].lowercase()
                        lastDetectedText.value = text
                        if (text.contains("help")) {
                            triggerAccidentDetection()
                        }
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }
    }

    private fun startVitalsSimulation() {
        serviceScope.launch {
            while (isActive) {
                if (isSimulationOn.value) {
                    heartRate.value = (60..100).random()
                    val sys = (110..130).random()
                    val dia = (70..90).random()
                    bloodPressure.value = "$sys/$dia"
                    
                    // Simulate RSSI dropping
                    watchRssi.value -= (0..3).random()
                    if (watchRssi.value < -95) watchRssi.value = -60
                }
                delay(2000)
            }
        }
    }

    private fun startMonitoring() {
        isMonitoring.value = true
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        
        val notification = createNotification("CraDet Active", "Protecting you with real-time monitoring")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startBluetoothMonitoring() {
        serviceScope.launch {
            while (isActive) {
                checkBluetoothStatus()
                delay(5000)
            }
        }
    }

    private fun checkBluetoothStatus() {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            watchConnected.value = false
            return
        }

        val pairedDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                adapter.bondedDevices
            } else {
                emptySet()
            }
        } else {
            adapter.bondedDevices
        }
        val targetDevice = pairedDevices.find { it.address == WATCH_MAC || it.name == WATCH_NAME }
        
        if (targetDevice != null) {
            watchConnected.value = isDeviceConnected(targetDevice)
        } else {
            watchConnected.value = false
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        val adapter = bluetoothManager.adapter
        return adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED ||
               adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SEND_SAFE_SMS" -> sendSafeSms()
            "ACTION_CANCEL_ALARM" -> cancelAlarm()
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelX.value = event.values[0]
                accelY.value = event.values[1]
                accelZ.value = event.values[2]
                val g = sqrt(accelX.value * accelX.value + accelY.value * accelY.value + accelZ.value * accelZ.value) / 9.81f
                
                // Always update gForce for real-time display as per user request
                gForce.value = g
                
                // Trigger if either the real g or a manual simulation was requested
                if (g > 4.5f || manualSimulationTrigger.value) {
                    manualSimulationTrigger.value = false
                    triggerAccidentDetection()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX.value = event.values[0]
                gyroY.value = event.values[1]
                gyroZ.value = event.values[2]
            }
        }
    }

    private var alarmJob: Job? = null

    fun triggerAccidentDetection() {
        if (accidentDetected.value || countdownActive.value) return
        countdownActive.value = true
        
        alarmJob = serviceScope.launch {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

            for (i in 20 downTo 1) {
                if (!isActive || !countdownActive.value) break
                
                countdownValue.value = i
                
                // Blink Torch
                setTorchState(i % 2 == 0)

                // Start a loud, continuous beep for the duration of the second
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
                } catch (e: Exception) { e.printStackTrace() }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
                delay(1000)
            }
            
            if (isActive && countdownActive.value) {
                accidentDetected.value = true
                countdownActive.value = false
                setTorchState(false)
                toneGenerator?.stopTone()
                // Reset gForce after simulation/detection
                if (isSimulationOn.value) gForce.value = 1.0f
                sendEmergencySms()
            }
        }
    }

    private fun setTorchState(enabled: Boolean) {
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, enabled)
            }
        } catch (e: Exception) {
            // Torch might not be available or already in use
        }
    }

    private fun sendEmergencySms() {
        // ... (existing implementation)
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@MonitoringService)
                val contacts = db.userDao().getAllContactsList()
                val profile = db.userDao().getUserProfileList()
                
                if (contacts.isEmpty()) {
                    android.util.Log.e("CraDetSMS", "No emergency contacts found!")
                    return@launch
                }

                val bloodType = profile?.bloodType ?: "Unknown"
                val abnormalitiesInfo = profile?.abnormalities ?: "None"
                val userName = profile?.email?.substringBefore("@") ?: "User"
                
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val timestamp = sdf.format(Date())

                // Fetch location with a shorter timeout to avoid delaying the SMS too much
                var locationLink = "Location: Unavailable"
                try {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        
                        val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        val location = withTimeoutOrNull(3000) { // 3 seconds timeout
                            suspendCancellableCoroutine { cont ->
                                locationTask.addOnSuccessListener { loc -> 
                                    if (cont.isActive) cont.resume(loc) 
                                }
                                locationTask.addOnFailureListener { _ -> 
                                    if (cont.isActive) cont.resume(null) 
                                }
                            }
                        }
                        if (location != null) {
                            locationLink = "Live Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                        } else {
                            // Try last known location if current is null
                            val lastLocationTask = fusedLocationClient.lastLocation
                            val lastLocation = withTimeoutOrNull(2000) {
                                suspendCancellableCoroutine { cont ->
                                    lastLocationTask.addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                                    lastLocationTask.addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
                                }
                            }
                            if (lastLocation != null) {
                                locationLink = "Last Known Location: https://maps.google.com/?q=${lastLocation.latitude},${lastLocation.longitude}"
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CraDetSMS", "Location fetch failed", e)
                }

                val message = "EMERGENCY ALERT!\n" +
                             "Possible accident detected for $userName.\n" +
                             "Blood Group: $bloodType\n" +
                             "Abnormalities: High impact (${"%.2f".format(gForce.value)}G) detected, $abnormalitiesInfo.\n" +
                             "$locationLink\n" +
                             "Time: $timestamp\n" +
                             "Please respond immediately."
                
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                contacts.forEach { contact ->
                    try {
                        // Use sendMultipartTextMessage for long messages
                        val parts = smsManager.divideMessage(message)
                        smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null)
                        android.util.Log.d("CraDetSMS", "Emergency SMS sent to ${contact.phone}")
                    } catch (e: Exception) {
                        android.util.Log.e("CraDetSMS", "Failed to send emergency SMS to ${contact.phone}", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CraDetSMS", "sendEmergencySms global failure", e)
            }
        }
    }

    fun cancelAlarm() {
        countdownActive.value = false
        alarmJob?.cancel()
        alarmJob = null
        toneGenerator?.stopTone()
        setTorchState(false)
        accidentDetected.value = false
        manualSimulationTrigger.value = false
    }

    fun sendSafeSms() {
        accidentDetected.value = false
        manualSimulationTrigger.value = false
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MonitoringService)
            val contacts = db.userDao().getAllContactsList()
            val message = "✅ I am safe now. No need to worry. Situation under control."
            
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            contacts.forEach { contact ->
                try {
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                    android.util.Log.d("CraDetSMS", "Safe SMS sent to ${contact.phone}")
                } catch (e: Exception) {
                    android.util.Log.e("CraDetSMS", "Failed to send safe SMS to ${contact.phone}", e)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "CraDet Monitoring Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        sensorManager.unregisterListener(this)
        speechRecognizer?.destroy()
        isMonitoring.value = false
        toneGenerator?.release()
    }
}
