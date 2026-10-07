package com.gameboost.lite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.graphics.drawable.GradientDrawable

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.roundBg(color: Int, rDp: Int): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = dp(rDp).toFloat() }

fun Service.fgNotification(text: String): Notification {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel("gb", "GameBoost Lite", NotificationManager.IMPORTANCE_LOW)
    )
    return Notification.Builder(this, "gb")
        .setContentTitle("GameBoost Lite")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .build()
}
