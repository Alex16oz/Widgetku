package com.example.widgetku

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.RemoteViews

class VolumeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val VOLUME_UP_ACTION = "com.example.widgetku.VOLUME_UP"
        const val VOLUME_DOWN_ACTION = "com.example.widgetku.VOLUME_DOWN"

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.volume_widget)

            val volumeUpIntent = Intent(context, VolumeWidgetProvider::class.java).apply {
                action = VOLUME_UP_ACTION
            }
            val volumeUpPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                volumeUpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.volume_up_button, volumeUpPendingIntent)

            val volumeDownIntent = Intent(context, VolumeWidgetProvider::class.java).apply {
                action = VOLUME_DOWN_ACTION
            }
            val volumeDownPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                volumeDownIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.volume_down_button, volumeDownPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (intent.action == VOLUME_UP_ACTION) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
        } else if (intent.action == VOLUME_DOWN_ACTION) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }
}