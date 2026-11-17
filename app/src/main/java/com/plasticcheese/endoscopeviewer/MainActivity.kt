package com.plasticcheese.endoscopeviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var session: CameraSession
    private var permissionPending = false
    private var permissionAsked = false
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionPending = false
        session.cameraPermissionResult()
        hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionPending = savedInstanceState?.getBoolean("permissionPending") ?: false
        permissionAsked = savedInstanceState?.getBoolean("permissionAsked") ?: false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        session = CameraSession(applicationContext, ::hasCameraPermission, ::requestCameraAutomatically)
        setContent { ViewerScreen(session, ::showCameraPermission) }
    }

    override fun onStart() {
        super.onStart()
        session.start()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        session.cameraPermissionResult()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        session.findCamera()
        session.cameraPermissionResult()
    }

    override fun onStop() {
        session.stop()
        super.onStop()
    }

    override fun onDestroy() {
        session.dispose()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("permissionPending", permissionPending)
        outState.putBoolean("permissionAsked", permissionAsked)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    private fun requestCameraAutomatically() {
        if (!permissionAsked) askForCameraPermission()
    }

    private fun showCameraPermission() {
        if (permissionPending) return
        if (permissionAsked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        } else {
            askForCameraPermission()
        }
    }

    private fun askForCameraPermission() {
        permissionAsked = true
        permissionPending = true
        requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
