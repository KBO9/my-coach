package fr.kbo9.mycoachsante

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/** Garde l'appli active pendant que la voix du coach parle, même écran verrouillé. */
class AudioService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("t") ?: "Voix du coach"
        val secs = intent?.getLongExtra("s", 0L) ?: 0L
        Notifs.channels(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, Notifs.CH_AUDIO)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("My Coach")
            .setContentText(title)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(7, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(stopper)
        handler.postDelayed(stopper, (if (secs > 0) secs + 20 else 3600L) * 1000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopper)
        super.onDestroy()
    }
}
