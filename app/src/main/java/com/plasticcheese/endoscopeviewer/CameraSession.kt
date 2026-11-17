package com.plasticcheese.endoscopeviewer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import com.serenegiant.utils.UVCUtils

internal enum class CameraStatus {
    Disconnected, Connecting, CameraPermission, UsbPermission, Streaming, Failed,
}

internal data class Resolution(val width: Int, val height: Int)

internal class CameraSession(
    private val context: Context,
    private val hasCameraPermission: () -> Boolean,
    private val requestCameraPermission: () -> Unit,
) {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private val failedToStart = Runnable { status = CameraStatus.Failed }

    private var helper: CameraHelper? = null
    private var device: UsbDevice? = null
    private var surface: SurfaceTexture? = null
    private var modes = emptyMap<Resolution, Size>()
    private var requestedMode: Size? = null
    private var deviceOpened = false
    private var opened = false
    private var usbRequest: String? = null
    private var usbRequestId = 0
    private var deniedUsb: String? = null

    var status by mutableStateOf(CameraStatus.Disconnected)
        private set
    var resolution by mutableStateOf<Resolution?>(null)
        private set
    var resolutions by mutableStateOf(emptyList<Resolution>())
        private set

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val grantedDevice = IntentCompat.getParcelableExtra(
                intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java,
            ) ?: return
            if (intent.action != permissionAction || usbRequest != grantedDevice.deviceName) return
            usbRequest = null
            if (!usb.hasPermission(grantedDevice)) deniedUsb = grantedDevice.deviceName
            if (device?.deviceName == grantedDevice.deviceName) connect()
        }
    }

    init {
        UVCUtils.init(context)
        ContextCompat.registerReceiver(
            context, permissionReceiver, IntentFilter(permissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun start() {
        if (helper != null) return
        val camera = CameraHelper()
        camera.videoCaptureConfig = camera.videoCaptureConfig.setAudioCaptureEnable(false)
        helper = camera
        camera.setStateCallback(Callbacks(camera))
        findCamera()
    }

    fun stop() {
        handler.removeCallbacks(failedToStart)
        helper?.apply {
            setStateCallback(null)
            releaseAll()
        }
        helper = null
        device = null
        clearPreview()
        status = CameraStatus.Disconnected
    }

    fun dispose() {
        stop()
        context.unregisterReceiver(permissionReceiver)
    }

    fun findCamera() {
        if (helper == null || device != null) return
        usb.deviceList.values.firstOrNull {
            it.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
                (0 until it.interfaceCount).any { index ->
                    it.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_VIDEO
                }
        }?.let {
            device = it
            if (usbRequest != it.deviceName) usbRequest = null
            connect()
        }
    }

    fun cameraPermissionResult() {
        if (device != null && status == CameraStatus.CameraPermission && hasCameraPermission()) {
            connect()
        }
    }

    fun retry() {
        val camera = helper ?: return
        if (device == null) {
            findCamera()
        } else if (deviceOpened && status == CameraStatus.Failed) {
            restartCamera(camera, requestedMode)
        } else {
            deniedUsb = null
            connect()
        }
    }

    fun setSurface(newSurface: SurfaceTexture?) {
        surface?.let { helper?.removeSurface(it) }
        surface = newSurface
        if (!opened) return
        handler.removeCallbacks(failedToStart)
        if (status != CameraStatus.Failed) status = CameraStatus.Connecting
        newSurface?.let {
            helper?.addSurface(it, false)
            watchForFrames()
        }
    }

    fun onFrame() {
        if (!opened || status == CameraStatus.Streaming) return
        handler.removeCallbacks(failedToStart)
        status = CameraStatus.Streaming
    }

    fun changeResolution(newResolution: Resolution) {
        val mode = modes[newResolution] ?: return
        val camera = helper ?: return
        if (!deviceOpened || status == CameraStatus.Connecting) return
        if (newResolution == resolution && status == CameraStatus.Streaming) return
        restartCamera(camera, mode)
    }

    private fun connect() {
        val camera = helper ?: return
        val selected = device ?: return
        when {
            !hasCameraPermission() -> {
                status = CameraStatus.CameraPermission
                requestCameraPermission()
            }
            usb.hasPermission(selected) -> {
                status = CameraStatus.Connecting
                watchForFrames()
                camera.selectDevice(selected)
            }
            deniedUsb == selected.deviceName -> status = CameraStatus.UsbPermission
            usbRequest == selected.deviceName -> status = CameraStatus.Connecting
            else -> {
                status = CameraStatus.Connecting
                usbRequest = selected.deviceName
                val flags = PendingIntent.FLAG_ONE_SHOT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(
                    context, ++usbRequestId,
                    Intent(permissionAction).setPackage(context.packageName),
                    flags,
                )
                try {
                    usb.requestPermission(selected, pending)
                } catch (_: IllegalArgumentException) {
                    usbRequest = null
                    status = CameraStatus.Failed
                } catch (_: SecurityException) {
                    usbRequest = null
                    status = CameraStatus.UsbPermission
                }
            }
        }
    }

    private fun restartCamera(camera: CameraHelper, mode: Size?) {
        opened = false
        requestedMode = mode
        status = CameraStatus.Connecting
        camera.closeCamera()
        camera.openCamera(mode)
        watchForFrames()
    }

    private fun watchForFrames() {
        handler.removeCallbacks(failedToStart)
        handler.postDelayed(failedToStart, 8_000)
    }

    private fun clearPreview() {
        deviceOpened = false
        opened = false
        modes = emptyMap()
        resolution = null
        resolutions = emptyList()
        requestedMode = null
    }

    private inner class Callbacks(private val camera: CameraHelper) : ICameraHelper.StateCallback {
        private fun isCurrent(candidate: UsbDevice) =
            helper === camera && device?.deviceName == candidate.deviceName

        override fun onAttach(device: UsbDevice) {
            if (helper === camera) findCamera()
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (!isCurrent(device)) return
            deviceOpened = true
            camera.openCamera(requestedMode)
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (!isCurrent(device)) return
            val activeSize = camera.previewSize
            if (activeSize == null) {
                status = CameraStatus.Failed
                return
            }
            modes = camera.supportedSizeList.orEmpty()
                .filter { it.width > 0 && it.height > 0 }
                .sortedWith(
                    compareByDescending<Size> { it.type == UVCCamera.DEFAULT_PREVIEW_FRAME_FORMAT }
                        .thenByDescending { it.fps },
                )
                .distinctBy { Resolution(it.width, it.height) }
                .associateBy { Resolution(it.width, it.height) }
            resolutions = modes.keys.sortedWith(
                compareByDescending<Resolution> { it.width.toLong() * it.height }
                    .thenByDescending { it.width },
            )
            resolution = Resolution(activeSize.width, activeSize.height)
            requestedMode = activeSize
            opened = true
            status = CameraStatus.Connecting
            surface?.let { camera.addSurface(it, false) }
            camera.startPreview()
            watchForFrames()
        }

        override fun onCameraClose(device: UsbDevice) = Unit

        override fun onDeviceClose(device: UsbDevice) {
            if (!isCurrent(device)) return
            deviceOpened = false
            opened = false
            handler.removeCallbacks(failedToStart)
            status = CameraStatus.Failed
        }

        override fun onDetach(device: UsbDevice) {
            if (helper !== camera) return
            if (usbRequest == device.deviceName) usbRequest = null
            if (deniedUsb == device.deviceName) deniedUsb = null
            if (!isCurrent(device)) return
            handler.removeCallbacks(failedToStart)
            this@CameraSession.device = null
            clearPreview()
            status = CameraStatus.Disconnected
            findCamera()
        }

        override fun onCancel(device: UsbDevice) {
            if (!isCurrent(device)) return
            handler.removeCallbacks(failedToStart)
            deniedUsb = device.deviceName
            status = CameraStatus.UsbPermission
        }

        override fun onError(device: UsbDevice, e: CameraException) {
            if (!isCurrent(device)) return
            opened = false
            handler.removeCallbacks(failedToStart)
            status = CameraStatus.Failed
        }
    }
}
