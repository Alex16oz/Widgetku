package com.example.widgetku

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import android.widget.SeekBar
import kotlin.math.abs

class FloatingVolumeService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var floatingWidgetView: View
    private lateinit var paramsButton: WindowManager.LayoutParams
    private lateinit var paramsWidget: WindowManager.LayoutParams
    private lateinit var audioManager: AudioManager
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private var isFlashlightOn = false
    private var isWidgetVisible = false

    // Handler untuk menutup widget utama saat tidak aktif
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { hideMainWidget() }
    private val INACTIVITY_TIMEOUT = 4000L

    // Handler untuk membuat tombol semi-transparan (fade out)
    private val buttonFadeHandler = Handler(Looper.getMainLooper())
    private val buttonFadeRunnable = Runnable { fadeOutButton() }
    private val BUTTON_FADE_TIMEOUT = 2000L
    private var isButtonFaded = false


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupWindowManager()
        setupViews()
        setupSystemServices()
        setupFloatingButtonTouchListener()
        setupMainWidgetListeners()
        windowManager.addView(floatingButtonView, paramsButton)
        startFadeOutTimer() // Mulai timer fade out saat service dibuat
    }

    private fun setupWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Parameter untuk tombol ikon floating
        paramsButton = WindowManager.LayoutParams(
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

        // Parameter untuk widget utama
        paramsWidget = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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

    private fun setupFloatingButtonTouchListener() {
        val actionButton = floatingButtonView.findViewById<Button>(R.id.floating_widget_button)

        actionButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var touchDownTime: Long = 0

            private val MAX_CLICK_DURATION = 200
            private val MAX_CLICK_DISTANCE = 15

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                // Saat disentuh, reset timer dan kembalikan tampilan tombol
                resetFadeOutTimer()
                fadeInButton()

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownTime = System.currentTimeMillis()
                        initialX = paramsButton.x
                        initialY = paramsButton.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        paramsButton.x = initialX + (event.rawX - initialTouchX).toInt()
                        paramsButton.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButtonView, paramsButton)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - touchDownTime
                        val xDistance = abs(event.rawX - initialTouchX)
                        val yDistance = abs(event.rawY - initialTouchY)

                        if (clickDuration < MAX_CLICK_DURATION && xDistance < MAX_CLICK_DISTANCE && yDistance < MAX_CLICK_DISTANCE) {
                            v.performClick() // Dianggap sebagai klik jika pergerakan minim
                        } else {
                            // Animasikan tombol ke tepi layar terdekat hanya jika digeser
                            animateToEdge()
                        }

                        // Selalu mulai timer fadeout setelah interaksi selesai
                        resetFadeOutTimer()
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
        val volumeUpButton = floatingWidgetView.findViewById<Button>(R.id.floating_volume_up_button)
        val volumeDownButton = floatingWidgetView.findViewById<Button>(R.id.floating_volume_down_button)
        val flashlightButton = floatingWidgetView.findViewById<Button>(R.id.floating_flashlight_button)
        val volumeSeekBar = floatingWidgetView.findViewById<SeekBar>(R.id.volume_seekbar)

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    resetInactivityTimer()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetInactivityTimer()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                resetInactivityTimer()
            }
        })

        volumeUpButton.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            resetInactivityTimer()
        }

        volumeDownButton.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            resetInactivityTimer()
        }

        flashlightButton.setOnClickListener {
            try {
                isFlashlightOn = !isFlashlightOn
                cameraManager.setTorchMode(cameraId, isFlashlightOn)
                resetInactivityTimer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Listener untuk widget utama agar bisa digeser
        floatingWidgetView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hideMainWidget()
                    return true
                }

                resetInactivityTimer()

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = paramsWidget.x
                        initialY = paramsWidget.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        paramsWidget.x = initialX + (event.rawX - initialTouchX).toInt()
                        paramsWidget.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingWidgetView, paramsWidget)
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
            // Hentikan timer fade out dan pastikan tombol terlihat normal
            cancelFadeOutTimer()
            fadeInButton()

            paramsWidget.x = paramsButton.x
            paramsWidget.y = paramsButton.y

            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            floatingWidgetView.findViewById<SeekBar>(R.id.volume_seekbar).progress = currentVolume

            windowManager.removeView(floatingButtonView)
            windowManager.addView(floatingWidgetView, paramsWidget)
            isWidgetVisible = true
            startInactivityTimer() // Mulai timer untuk menyembunyikan widget
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideMainWidget() {
        if (!isWidgetVisible) return
        try {
            paramsButton.x = paramsWidget.x
            paramsButton.y = paramsWidget.y

            windowManager.removeView(floatingWidgetView)
            windowManager.addView(floatingButtonView, paramsButton)
            isWidgetVisible = false
            cancelInactivityTimer()
            startFadeOutTimer() // Mulai lagi timer fade out setelah widget ditutup
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startInactivityTimer() {
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT)
    }

    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT)
    }

    private fun cancelInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun startFadeOutTimer() {
        buttonFadeHandler.postDelayed(buttonFadeRunnable, BUTTON_FADE_TIMEOUT)
    }

    private fun resetFadeOutTimer() {
        buttonFadeHandler.removeCallbacks(buttonFadeRunnable)
        buttonFadeHandler.postDelayed(buttonFadeRunnable, BUTTON_FADE_TIMEOUT)
    }

    private fun cancelFadeOutTimer() {
        buttonFadeHandler.removeCallbacks(buttonFadeRunnable)
    }

    private fun fadeOutButton() {
        if (isButtonFaded || !floatingButtonView.isAttachedToWindow) return
        isButtonFaded = true

        val animator = ValueAnimator.ofFloat(1f, 0.5f).apply {
            duration = 500 // durasi animasi 0.5 detik
            addUpdateListener {
                val value = it.animatedValue as Float
                floatingButtonView.alpha = value
                floatingButtonView.scaleX = value
                floatingButtonView.scaleY = value
                // TIDAK PERLU updateViewLayout di sini karena hanya mengubah properti view
            }
            // Setelah fade out, jalankan animasi ke tepi layar
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    if (floatingButtonView.isAttachedToWindow) {
                        animateToEdge()
                    }
                }
            })
        }
        animator.start()
    }

    private fun fadeInButton() {
        if (!isButtonFaded || !floatingButtonView.isAttachedToWindow) return
        isButtonFaded = false

        val currentScale = floatingButtonView.scaleX
        val animator = ValueAnimator.ofFloat(currentScale, 1f).apply {
            duration = 300 // durasi animasi 0.3 detik
            addUpdateListener {
                val value = it.animatedValue as Float
                floatingButtonView.alpha = value
                floatingButtonView.scaleX = value
                floatingButtonView.scaleY = value
                // TIDAK PERLU updateViewLayout di sini
            }
        }
        animator.start()
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            windowMetrics.bounds.width()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    private fun animateToEdge() {
        if (!floatingButtonView.isAttachedToWindow) return

        val screenWidth = getScreenWidth()
        val middle = screenWidth / 2
        // Tentukan posisi akhir X: 0 (kiri) atau tepi kanan layar
        val finalX = if (paramsButton.x < middle) 0 else screenWidth - floatingButtonView.width

        val animator = ValueAnimator.ofInt(paramsButton.x, finalX).apply {
            duration = 300 // durasi animasi 0.3 detik
            addUpdateListener {
                paramsButton.x = it.animatedValue as Int
                windowManager.updateViewLayout(floatingButtonView, paramsButton)
            }
        }
        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelInactivityTimer()
        cancelFadeOutTimer()
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