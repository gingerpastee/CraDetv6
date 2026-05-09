package com.example.cradetv6

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.cradetv6.data.AppDatabase
import com.example.cradetv6.ui.LoginScreen
import com.example.cradetv6.ui.ProfileSetupScreen
import com.example.cradetv6.ui.theme.CraDetv6Theme
import com.google.android.gms.maps.model.*
import com.google.firebase.database.FirebaseDatabase
import com.google.maps.android.compose.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = FirebaseDatabase.getInstance(
            "https://cardat-1d7d3-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        val ref = database.getReference("vehicleData")
        val db = AppDatabase.getDatabase(this)
        
        lifecycleScope.launch {
            while (coroutineContext.isActive) {
                val userProfile = db.userDao().getUserProfileList()
                val data = mapOf(
                    "gyroscope" to mapOf(
                        "x" to MonitoringService.gyroX.value,
                        "y" to MonitoringService.gyroY.value,
                        "z" to MonitoringService.gyroZ.value
                    ),
                    "accelerometer" to mapOf(
                        "x" to MonitoringService.accelX.value,
                        "y" to MonitoringService.accelY.value,
                        "z" to MonitoringService.accelZ.value
                    ),
                    "crash_detection" to mapOf(
                        "accident_detected" to MonitoringService.accidentDetected.value,
                        "g_force" to MonitoringService.gForce.value
                    ),
                    "location" to mapOf(
                        "latitude" to (MonitoringService.currentLocation.value?.latitude ?: 0.0),
                        "longitude" to (MonitoringService.currentLocation.value?.longitude ?: 0.0)
                    ),
                    "emergency_alert" to mapOf(
                        "active" to MonitoringService.countdownActive.value,
                        "countdown" to MonitoringService.countdownValue.value
                    ),
                    "user_vitals" to mapOf(
                        "blood_type" to (userProfile?.bloodType ?: "Unknown"),
                        "heart_rate" to MonitoringService.heartRate.value,
                        "blood_pressure" to MonitoringService.bloodPressure.value,
                        "watch_rssi" to MonitoringService.watchRssi.value
                    )
                )
                ref.setValue(data)
                delay(1000)
            }
        }

        enableEdgeToEdge()
        setContent {
            CraDetv6Theme {
                MainApp()
            }
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startMonitoringService()
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val userProfile by viewModel.userProfile.collectAsState()
    
    val startDestination = if (userProfile == null) "login" else "dashboard"

    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.VIBRATE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest.toTypedArray())
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("profile_setup") },
                viewModel = viewModel
            )
        }
        composable("profile_setup") {
            ProfileSetupScreen(
                onComplete = { navController.navigate("dashboard") },
                viewModel = viewModel
            )
        }
        composable("dashboard") {
            DashboardScreen(viewModel, navController)
        }
        composable("navigation_map") {
            NavigationMapScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavHostController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CraDet Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("navigation_map") }) {
                        Icon(Icons.Default.Map, contentDescription = "Navigation", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { 
                        viewModel.logout()
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { innerPadding ->
        DashboardContent(modifier = Modifier.padding(innerPadding), viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMapScreen(onBack: () -> Unit) {
    val startLoc = LatLng(12.9716, 77.5946) // MG Road, Bangalore
    val destLoc = LatLng(12.9250, 77.5890) // Jayanagar, Bangalore
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.9500, 77.5900), 13f)
    }

    // Realistic road-following path coordinates (simulated segment)
    val roadPath = listOf(
        LatLng(12.9716, 77.5946),
        LatLng(12.9650, 77.5940),
        LatLng(12.9580, 77.5930),
        LatLng(12.9500, 77.5910),
        LatLng(12.9420, 77.5895),
        LatLng(12.9350, 77.5890),
        LatLng(12.9250, 77.5890)
    )

    // Example accident prone zones with smaller radius (150m instead of 500m)
    val dangerZones = listOf(
        LatLng(12.9650, 77.5940) to Color.Red,
        LatLng(12.9540, 77.5920) to Color.Yellow,
        LatLng(12.9420, 77.5895) to Color.Red,
        LatLng(12.9300, 77.5890) to Color.Yellow
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe Navigation") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Search bars (UI placeholders)
            Card(Modifier.padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = "Current Location",
                        onValueChange = {},
                        label = { Text("Start") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "Jayanagar 4th Block",
                        onValueChange = {},
                        label = { Text("Destination") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            GoogleMap(
                modifier = Modifier.weight(1f),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true)
            ) {
                Marker(state = rememberMarkerState(position = startLoc), title = "Start")
                Marker(state = rememberMarkerState(position = destLoc), title = "Destination")
                
                // Realistic road-following Polyline
                Polyline(
                    points = roadPath,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    width = 12f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )

                // Refined, smaller accident prone zones (150 meters)
                dangerZones.forEach { (pos, color) ->
                    Circle(
                        center = pos,
                        radius = 150.0, // Significantly smaller area
                        fillColor = color.copy(alpha = 0.4f),
                        strokeColor = color,
                        strokeWidth = 3f
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardContent(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val context = LocalContext.current
    val isMonitoring by MonitoringService.isMonitoring.collectAsState()
    val watchConnected by MonitoringService.watchConnected.collectAsState()
    val gForce by MonitoringService.gForce.collectAsState()
    val accelX by MonitoringService.accelX.collectAsState()
    val accelY by MonitoringService.accelY.collectAsState()
    val accelZ by MonitoringService.accelZ.collectAsState()
    val gyroX by MonitoringService.gyroX.collectAsState()
    val gyroY by MonitoringService.gyroY.collectAsState()
    val gyroZ by MonitoringService.gyroZ.collectAsState()
    
    val heartRate by MonitoringService.heartRate.collectAsState()
    val bloodPressure by MonitoringService.bloodPressure.collectAsState()
    val isSimulationOn by MonitoringService.isSimulationOn.collectAsState()
    val watchRssi by MonitoringService.watchRssi.collectAsState()
    
    val countdownActive by MonitoringService.countdownActive.collectAsState()
    val countdownValue by MonitoringService.countdownValue.collectAsState()
    val accidentDetected by MonitoringService.accidentDetected.collectAsState()
    val lastDetectedText by MonitoringService.lastDetectedText.collectAsState()

    val contacts by viewModel.contacts.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AnimatedVisibility(visible = countdownActive) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEB3B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚨 ACCIDENT ALERT 🚨", fontWeight = FontWeight.Bold, color = Color.Red)
                        Text("Sending SMS in $countdownValue seconds", fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                // Directly trigger service cancellation to ensure all jobs stop
                                val cancelIntent = Intent(context, MonitoringService::class.java).apply {
                                    action = "ACTION_CANCEL_ALARM"
                                }
                                context.startService(cancelIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("I AM SAFE - CANCEL")
                        }
                    }
                }
            }
        }

        item {
            if (accidentDetected) {
                Button(
                    onClick = { 
                        // Start service and call sendSafeSms through a broadcast or direct intent
                        // For a simple fix in this context, we can send a broadcast to the service
                        // but let's just trigger it via a simple trick since MonitoringService is singleton-ish with its companion
                        // Actually, let's just make the accidentDetected flag change trigger the SMS in the service
                        MonitoringService.accidentDetected.value = false
                        val safeSmsIntent = Intent(context, MonitoringService::class.java).apply {
                            action = "ACTION_SEND_SAFE_SMS"
                        }
                        context.startService(safeSmsIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("SEND 'I AM SAFE' SMS")
                }
            } else {
                Button(
                    onClick = { 
                        MonitoringService.manualSimulationTrigger.value = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text("SIMULATE ACCIDENT")
                }
            }
        }

        item {
            StatusCard(
                title = "Monitoring Status",
                status = if (isMonitoring) "🟢 Active" else "🔴 Inactive",
                icon = Icons.Default.Security,
                color = if (isMonitoring) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Voice Monitor", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                        Text(
                            text = if (lastDetectedText.isEmpty()) "Listening..." else "\"$lastDetectedText\"",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (lastDetectedText.isEmpty()) Color.Gray else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Simulation Mode", fontWeight = FontWeight.Bold)
                        Text("Fake Vitals & RSSI", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isSimulationOn, onCheckedChange = { MonitoringService.isSimulationOn.value = it })
                }
            }
        }

        item {
            WatchStatusCard(
                connected = watchConnected,
                name = MonitoringService.WATCH_NAME,
                mac = MonitoringService.WATCH_MAC,
                rssi = watchRssi,
                hr = heartRate,
                bp = bloodPressure,
                simulationOn = isSimulationOn
            )
        }

        item {
            SensorGrid(accelX, accelY, accelZ, gyroX, gyroY, gyroZ, gForce)
        }

        item {
            Text("Emergency Contacts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                var name by remember { mutableStateOf("") }
                var phone by remember { mutableStateOf("") }
                
                Column(Modifier.padding(16.dp)) {
                    Text("Add New Contact", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && phone.isNotEmpty()) {
                                viewModel.addContact(name, phone)
                                name = ""
                                phone = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Contact")
                    }
                }
            }
        }

        items(contacts) { contact ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("👤 ${contact.name}", fontWeight = FontWeight.Bold)
                        Text("📞 ${contact.phone}")
                    }
                    IconButton(onClick = { viewModel.deleteContact(contact) }) {
                        Icon(Icons.Default.Delete, tint = Color.Red, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun WatchStatusCard(connected: Boolean, name: String, mac: String, rssi: Int, hr: Int, bp: String, simulationOn: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Watch, 
                    contentDescription = null, 
                    tint = if (connected || simulationOn) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Watch Status", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Text(
                        if (connected || simulationOn) "⌚ Connected to $name" else "❌ Watch Not Connected",
                        fontWeight = FontWeight.Bold,
                        color = if (connected || simulationOn) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
            if (connected || simulationOn) {
                Spacer(Modifier.height(4.dp))
                Text("MAC: $mac", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("📶 RSSI: $rssi dBm", style = MaterialTheme.typography.bodySmall)
                    Text("📏 Est. Distance: ${"%.1f".format(if (rssi > -60) 0.5 else 2.0)}m", style = MaterialTheme.typography.bodySmall)
                }
                
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    VitalItem("❤️ $hr bpm")
                    VitalItem("🩺 $bp")
                }
            }
        }
    }
}

@Composable
fun VitalItem(text: String) {
    Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
    }
}

@Composable
fun SensorGrid(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, g: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Live Sensor Data", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                SensorItem("Accel", "X: ${"%.2f".format(ax)}\nY: ${"%.2f".format(ay)}\nZ: ${"%.2f".format(az)}", Modifier.weight(1f))
                SensorItem("Gyro", "X: ${"%.2f".format(gx)}\nY: ${"%.2f".format(gy)}\nZ: ${"%.2f".format(gz)}", Modifier.weight(1f))
                SensorItem("Impact", "${"%.2f".format(g)} G", Modifier.weight(0.7f), color = if (g > 5.5) Color.Red else Color.Black)
            }
        }
    }
}

@Composable
fun SensorItem(label: String, value: String, modifier: Modifier, color: Color = Color.Black) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = color)
    }
}

@Composable
fun StatusCard(title: String, status: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text(status, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}
