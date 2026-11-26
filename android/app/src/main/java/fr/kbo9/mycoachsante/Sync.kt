package fr.kbo9.mycoachsante

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Sync {
    const val PATH = "sante.json"

    /** Lit Health Connect et envoie sante.json ; renvoie un résumé. */
    suspend fun run(c: Context, why: String): String = withContext(Dispatchers.IO) {
        if (!Prefs.linked(c)) throw IllegalStateException("Relie d’abord l’appli à My Coach (étape 3)")
        if (!Health.available(c)) throw IllegalStateException("Health Connect n’est pas disponible")
        val data = Health.collect(c)
        val stamp = SimpleDateFormat("d MMM HH:mm", Locale.FRANCE).format(Date())
        val sha = Github.put(Prefs.token(c), Prefs.repo(c), PATH, data.toString(), "Santé · $stamp ($why)", Prefs.sha(c))
        Prefs.setSha(c, sha)
        val summary = "${data.getJSONArray("sleep").length()} nuits · ${data.getJSONObject("steps").length()} jours de pas · ${data.getJSONArray("weight").length()} pesées"
        Prefs.ok(c, summary)
        summary
    }

    /** Synchro automatique toutes les 2 heures, dès qu'il y a du réseau. */
    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("sante", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Sync.run(applicationContext, "auto")
            Result.success()
        } catch (e: SecurityException) {
            Prefs.fail(applicationContext, "Lecture en arrière-plan refusée : autorise l’accès en arrière-plan (étape 1)")
            Result.success()
        } catch (e: Exception) {
            Prefs.fail(applicationContext, e.message ?: "Erreur pendant la synchro")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
