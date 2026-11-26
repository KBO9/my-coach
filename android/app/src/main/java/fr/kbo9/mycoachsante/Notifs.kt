package fr.kbo9.mycoachsante

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.util.Calendar

/** Notifications : fin du repos (écran verrouillé) et rappels de séance / pesée. */
object Notifs {
    const val CH_REST = "repos"
    const val CH_REMIND = "rappels"
    const val CH_AUDIO = "voix"
    private const val ACT_REST = "fr.kbo9.mycoachsante.REPOS"
    private const val ACT_REMIND = "fr.kbo9.mycoachsante.RAPPEL"

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val rest = NotificationChannel(CH_REST, "Fin du repos", NotificationManager.IMPORTANCE_HIGH)
        rest.enableVibration(true)
        rest.vibrationPattern = longArrayOf(0, 260, 120, 260)
        nm.createNotificationChannel(rest)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Rappels de séance et de pesée", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_AUDIO, "Voix du coach", NotificationManager.IMPORTANCE_LOW))
    }

    private fun canPost(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(c: Context, id: Int, channel: String, title: String, body: String) {
        if (!canPost(c)) return
        channels(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val rest = channel == CH_REST
        val b = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(if (rest) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (rest) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
        if (rest) b.setTimeoutAfter(10 * 60 * 1000L)
        val n = b.build()
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (e: SecurityException) {
            // notifications refusées
        }
    }

    private fun alarmIntent(c: Context, code: Int, action: String, title: String, body: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).putExtra("t", title).putExtra("b", body).putExtra("id", code)
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun exact(c: Context, at: Long, pi: PendingIntent) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** at = 0 : annule. */
    fun scheduleRest(c: Context, at: Long, title: String, body: String) {
        val pi = alarmIntent(c, 100, ACT_REST, title, body)
        c.getSystemService(AlarmManager::class.java)?.cancel(pi)
        NotificationManagerCompat.from(c).cancel(1)
        if (at > System.currentTimeMillis()) exact(c, at, pi)
    }

    /** json : [{ id, h, m, days: [0 = dimanche … 6], t, b }] */
    fun setReminders(c: Context, json: String) {
        c.getSharedPreferences("mcs", Context.MODE_PRIVATE).edit().putString("rappels", json).apply()
        scheduleReminders(c)
    }

    fun scheduleReminders(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        for (k in 0..15) am.cancel(alarmIntent(c, 200 + k, ACT_REMIND, "", ""))
        val arr = try {
            JSONArray(c.getSharedPreferences("mcs", Context.MODE_PRIVATE).getString("rappels", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val now = System.currentTimeMillis()
        for (n in 0 until arr.length()) {
            val r = arr.optJSONObject(n) ?: continue
            val id = r.optInt("id", n).coerceIn(0, 15)
            val days = r.optJSONArray("days") ?: continue
            val set = HashSet<Int>()
            for (k in 0 until days.length()) set.add(days.optInt(k))
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, r.optInt("h", 18))
            cal.set(Calendar.MINUTE, r.optInt("m", 0))
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            var found = false
            for (k in 0..7) {
                val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
                if (cal.timeInMillis > now + 30000 && dow in set) {
                    found = true
                    break
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            if (found) exact(c, cal.timeInMillis, alarmIntent(c, 200 + id, ACT_REMIND, r.optString("t"), r.optString("b")))
        }
    }

    fun onAlarm(c: Context, i: Intent) {
        when (i.action) {
            ACT_REST -> if (!MainActivity.visible) show(c, 1, CH_REST, i.getStringExtra("t") ?: "C’est reparti !", i.getStringExtra("b") ?: "")
            ACT_REMIND -> {
                show(c, 10 + (i.getIntExtra("id", 200) - 200), CH_REMIND, i.getStringExtra("t") ?: "My Coach", i.getStringExtra("b") ?: "")
                scheduleReminders(c)
            }
            else -> scheduleReminders(c)
        }
    }
}

/** Réveils programmés + redémarrage du téléphone / mise à jour de l'appli. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifs.onAlarm(context, intent)
    }
}
