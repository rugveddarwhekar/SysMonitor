
package com.example.sysmonitor

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class MonitorService : Service() {
    companion object {
        const val CHANNEL_ID = "sys_monitor_channel"
        const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var overlayView: TextView
    private lateinit var activityManager: ActivityManager

    private var lastTime = 0L
    private var frameCount = 0
    private var choreoCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastTime == 0L) {
                lastTime = frameTimeNanos
            }
            frameCount++

            val diff = frameTimeNanos - lastTime
            if (diff >= 1_000_000_000L) {
                val fps = frameCount
                updateOverlay(fps)
                frameCount = 0
                lastTime = frameTimeNanos
            }

            if (overlayView != null) {
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }

        private fun updateOverlay(fps: Int) {

            val view = overlayView ?: return

            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalMem = memInfo.totalMem / (1024*1024)
            val availMem = memInfo.availMem / (1024*1024)
            val usedMem = (totalMem - availMem).toInt()
            val usedPercentage = (usedMem.toDouble() / totalMem.toDouble() * 100).toInt()

            if (::overlayView.isInitialized && overlayView != null) {
                overlayView.text = "FPS: $fps\nRAM: $usedPercentage% ($usedMem MB / $totalMem MB)"

                if (fps >= 58) {
                    overlayView.setTextColor(Color.GREEN)
                } else if (fps >= 30) {
                    overlayView.setTextColor(Color.YELLOW)
                } else {
                    overlayView.setTextColor(Color.RED)
                }
            }
        }

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        startForegroundService()
        setupOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.view.Choreographer.getInstance().removeFrameCallback(choreoCallback)
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SysMonitor Running")
            .setContentText("Overlay is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = TextView(this).apply {
            text = "FPS: -- | RAM: --"
            setTextColor(Color.CYAN)
            textSize = 14f
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(20, 20, 20, 20)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 50

        overlayView?.setOnTouchListener (
            object : android.view.View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(
                    v: android.view.View,
                    event: android.view.MotionEvent
                ): Boolean {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }

                        android.view.MotionEvent.ACTION_MOVE -> {
                            val deltaX = (event.rawX - initialTouchX).toInt()
                            val deltaY = (event.rawY - initialTouchY).toInt()

                            params.x = initialX + deltaX
                            params.y = initialY + deltaY

                            if (overlayView != null) {
                                windowManager.updateViewLayout(overlayView, params)
                            }
                            return true
                        }
                    }
                    return false
                }
            }
        )

        try {
            if (overlayView.parent == null) {
                windowManager.addView(overlayView, params)
            }
            Choreographer.getInstance().postFrameCallback(choreoCallback)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }

        android.view.Choreographer.getInstance().postFrameCallback(choreoCallback)
    }

}
