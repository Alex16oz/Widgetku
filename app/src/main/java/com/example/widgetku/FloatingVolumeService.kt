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

    // Handler untuk menyembunyikan widget utama jika tidak aktif
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { hideMainWidget() }
    private val INACTIVITY_TIMEOUT = 5000L

    // Handler untuk memulai animasi fade out
    private val buttonFadeHandler = Handler(Looper.getMainLooper())
    private val buttonFadeRunnable = Runnable { fadeOutButton() }
    private val BUTTON_FADE_TIMEOUT = 3000L
    private var isButtonFaded = false

    // Animator yang sedang berjalan untuk mencegah konflik
    private var currentAnimator: Animator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupWindowManager()
        setupViews()
        setupSystemServices()
        setupFloatingButtonTouchListener()
        setupMainWidgetListeners()
        windowManager.addView(floatingButtonView, paramsButton)
        startFadeOutTimer()
    }

    private fun setupWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

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
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Hentikan semua timer dan animasi yang sedang berjalan
                        cancelFadeOutTimer()
                        currentAnimator?.cancel()
                        fadeInButton() // Kembalikan ke tampilan normal

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
                            v.performClick()
                        } else {
                            // Jika digeser, langsung pindah ke tepi
                            animateToEdge()
                        }

                        // Mulai lagi timer untuk fade out
                        startFadeOutTimer()
                        return true
                    }
                }
                return false
            }
        })

        actionButton.setOnClickListener { showMainWidget() }
    }

    // --- Tidak ada perubahan di fungsi-fungsi ini ---
    private fun setupMainWidgetListeners() {
        // ... (kode tetap sama)
    }
    private fun showMainWidget() {
        // ... (kode tetap sama)
    }
    private fun hideMainWidget() {
        // ... (kode tetap sama)
    }
    private fun startInactivityTimer() { inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT) }
    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT)
    }
    private fun cancelInactivityTimer() { inactivityHandler.removeCallbacks(inactivityRunnable) }
    private fun startFadeOutTimer() { buttonFadeHandler.postDelayed(buttonFadeRunnable, BUTTON_FADE_TIMEOUT) }
    private fun cancelFadeOutTimer() { buttonFadeHandler.removeCallbacks(buttonFadeRunnable) }
    // --- Akhir dari fungsi yang tidak berubah ---


    /**
     * Memudarkan tombol (menjadi kecil dan transparan), KEMUDIAN memindahkannya ke tepi.
     */
    private fun fadeOutButton() {
        if (isButtonFaded || !floatingButtonView.isAttachedToWindow) return
        isButtonFaded = true

        currentAnimator?.cancel() // Batalkan animasi sebelumnya

        val animator = ValueAnimator.ofFloat(1f, 0.5f).apply {
            duration = 500
            addUpdateListener {
                val value = it.animatedValue as Float
                floatingButtonView.alpha = value
                floatingButtonView.scaleX = value
                floatingButtonView.scaleY = value
            }
            // TAMBAHKAN LISTENER: Ini adalah kunci perbaikannya.
            // Panggil animateToEdge() HANYA SETELAH animasi fade selesai.
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    animateToEdge()
                }
            })
        }
        currentAnimator = animator
        currentAnimator?.start()
    }

    /**
     * Mengembalikan tampilan tombol ke normal.
     */
    private fun fadeInButton() {
        if (isButtonFaded) {
            isButtonFaded = false
            currentAnimator?.cancel() // Batalkan animasi fade out jika sedang berjalan
            val currentScale = floatingButtonView.scaleX
            val animator = ValueAnimator.ofFloat(currentScale, 1f).apply {
                duration = 300
                addUpdateListener {
                    val value = it.animatedValue as Float
                    floatingButtonView.alpha = value
                    floatingButtonView.scaleX = value
                    floatingButtonView.scaleY = value
                }
            }
            currentAnimator = animator
            currentAnimator?.start()
        }
    }

    /**
     * Menganimasikan posisi tombol ke tepi layar terdekat.
     */
    private fun animateToEdge() {
        if (!floatingButtonView.isAttachedToWindow) return

        val screenWidth = getScreenWidth()
        val finalX = if (paramsButton.x < screenWidth / 2) 0 else screenWidth - floatingButtonView.width

        // Hanya jalankan jika posisi belum di tepi
        if (paramsButton.x == finalX) return

        val moveAnimator = ValueAnimator.ofInt(paramsButton.x, finalX).apply {
            duration = 300
            addUpdateListener {
                paramsButton.x = it.animatedValue as Int
                windowManager.updateViewLayout(floatingButtonView, paramsButton)
            }
        }
        currentAnimator = moveAnimator
        currentAnimator?.start()
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentAnimator?.cancel()
        cancelInactivityTimer()
        cancelFadeOutTimer()
        try {
            if (this::windowManager.isInitialized) {
                if (floatingButtonView.isAttachedToWindow) windowManager.removeView(floatingButtonView)
                if (floatingWidgetView.isAttachedToWindow) windowManager.removeView(floatingWidgetView)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}