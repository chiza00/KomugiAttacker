package com.komugi.attacker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.komugi.attacker.ui.MainScreen
import com.komugi.attacker.ui.theme.KomugiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KomugiTheme {
                KomugiApp()
            }
        }
    }
}



@Composable
private fun KomugiApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingStart = remember { mutableStateOf(false) }
    val awaitingPermission = remember { mutableStateOf(false) }

    fun toast(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun startEngineNow() {
        if (!isBluetoothEnabled(context)) {
            toast("Bluetooth is off вЂ” turn it on first")
            return
        }
        viewModel.start()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        awaitingPermission.value = false
        val bleGranted = result.filterKeys { it in blePermissions() }.values.all { it }
        if (bleGranted && pendingStart.value) {
            pendingStart.value = false
            startEngineNow()
        } else if (!bleGranted) {
            pendingStart.value = false
            toast("Bluetooth permissions denied вЂ” engine cannot reach the radio")
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && pendingStart.value) {
            pendingStart.value = false
            viewModel.start()
        } else if (pendingStart.value) {
            pendingStart.value = false
            toast("Bluetooth stayed off вЂ” engine not started")
        }
    }

    fun requestStart() {
        pendingStart.value = true
        if (!hasBlePermissions(context)) {
            awaitingPermission.value = true
            permissionLauncher.launch(requiredPermissions())
            return
        }
        if (!isBluetoothEnabled(context)) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        pendingStart.value = false
        viewModel.start()
    }

    // A permission dialog result that arrives after a config change still needs to start.
    LaunchedEffect(awaitingPermission.value) {
        if (!awaitingPermission.value && pendingStart.value && hasBlePermissions(context)) {
            pendingStart.value = false
            startEngineNow()
        }
    }

    MainScreen(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onStart = ::requestStart,
        onStop = viewModel::stop,
    )
}

private fun hasBlePermissions(context: Context): Boolean = blePermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun blePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun requiredPermissions(): Array<String> {
    val permissions = blePermissions().toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

private fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled == true
}
