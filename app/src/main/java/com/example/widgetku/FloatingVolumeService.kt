package com.example.widgetku

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.SeekBar
import kotlin.math.abs

class FloatingVolumeService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var floatingWidgetView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var audioManager: AudioManager
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private var isFlashlightOn = false
    private var isWidgetVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupWindowManager()
        setupViews()
        setupSystemServices()
        setupFloatingButtonTouchListener() // Perbaikan krusial ada di sini
        setupMainWidgetListeners()
        windowManager.addView(floatingButtonView, params)
    }

    private fun setupWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }

    private fun setupViews() {
        val inflater = LayoutInflater.from(this)
        floatingButtonView = inflater.inflate(R.layout.floating_button, null)
        floatingWidgetView = inflater.inflate(R.layout.floating_volume_widget, null)
    }

    private fun setupSystemServices() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]
    }

    // --- PERBAIKAN KRUSIAL: LISTENER DITEMPATKAN LANGSUNG PADA BUTTON ---
    private fun setupFloatingButtonTouchListener() {
        // Ambil referensi button dari layout
        val actionButton = floatingButtonView.findViewById<Button>(R.id.floating_widget_button)

        actionButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var touchDownTime: Long = 0

            private val MAX_CLICK_DURATION = 200 // milidetik
            private val MAX_CLICK_DISTANCE = 15  // pixel

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownTime = System.currentTimeMillis()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButtonView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - touchDownTime
                        val xDistance = abs(event.rawX - initialTouchX)
                        val yDistance = abs(event.rawY - initialTouchY)

                        if (clickDuration < MAX_CLICK_DURATION && xDistance < MAX_CLICK_DISTANCE && yDistance < MAX_CLICK_DISTANCE) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        actionButton.setOnClickListener {
            showMainWidget()
        }
    }

    private fun setupMainWidgetListeners() {
        // (Tidak ada perubahan di sini)
        val volumeUpButton = floatingWidgetView.findViewById<Button>(R.id.floating_volume_up_button)
        val volumeDownButton = floatingWidgetView.findViewById<Button>(R.id.floating_volume_down_button)
        val flashlightButton = floatingWidgetView.findViewById<Button>(R.id.floating_flashlight_button)
        val volumeSeekBar = floatingWidgetView.findViewById<SeekBar>(R.id.volume_seekbar)
        val closeButton = floatingWidgetView.findViewById<Button>(R.id.close_button)

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        volumeUpButton.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        volumeDownButton.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        flashlightButton.setOnClickListener {
            try {
                isFlashlightOn = !isFlashlightOn
                cameraManager.setTorchMode(cameraId, isFlashlightOn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        closeButton.setOnClickListener { hideMainWidget() }

        // Touch listener untuk widget utama agar bisa digeser
        floatingWidgetView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingWidgetView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun showMainWidget() {
        if (isWidgetVisible) return
        try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            floatingWidgetView.findViewById<SeekBar>(R.id.volume_seekbar).progress = currentVolume

            windowManager.removeView(floatingButtonView)
            windowManager.addView(floatingWidgetView, params)
            isWidgetVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideMainWidget() {
        if (!isWidgetVisible) return
        try {
            windowManager.removeView(floatingWidgetView)
            windowManager.addView(floatingButtonView, params)
            isWidgetVisible = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (this::windowManager.isInitialized) {
                if (floatingButtonView.isAttachedToWindow) windowManager.removeView(floatingButtonView)
                if (floatingWidgetView.isAttachedToWindow) windowManager.removeView(floatingWidgetView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}