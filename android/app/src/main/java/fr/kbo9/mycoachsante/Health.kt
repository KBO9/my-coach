package fr.kbo9.mycoachsante

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Lecture de Health Connect, où Samsung Health range sommeil, pas et poids. */
object Health {
    const val PROVIDER = "com.google.android.apps.healthdata"
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    val SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
    val WEIGHT: String = HealthPermission.getReadPermission(WeightRecord::class)
    val DATA_PERMS: Set<String> = setOf(SLEEP, STEPS, WEIGHT)
    val ALL_PERMS: Set<String> = DATA_PERMS + BACKGROUND

    fun status(c: Context): Int = HealthConnectClient.getSdkStatus(c, PROVIDER)
    fun available(c: Context): Boolean = status(c) == HealthConnectClient.SDK_AVAILABLE
    fun client(c: Context): HealthConnectClient = HealthConnectClient.getOrCreate(c)

    suspend fun granted(c: Context): Set<String> = client(c).permissionController.getGrantedPermissions()

    private suspend fun <T : Record> readAll(hc: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val resp = hc.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = token
                )
            )
            out.addAll(resp.records)
            token = resp.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    /** Rassemble les 35 derniers jours au format attendu par My Coach (sante.json). */
    suspend fun collect(c: Context, days: Long = 35): JSONObject {
        val hc = client(c)
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val from = now.minus(Duration.ofDays(days))
        val granted = hc.permissionController.getGrantedPermissions()

        val o = JSONObject()
        o.put("app", "my-coach-sante")
        o.put("v", 1)
        o.put("at", System.currentTimeMillis())
        o.put("tz", zone.id)
        o.put("device", Build.MODEL)

        // Sommeil : une entrée par session, avec le temps passé dans chaque phase (minutes)
        val sleep = JSONArray()
        if (SLEEP in granted) {
            for (s in readAll(hc, SleepSessionRecord::class, from, now)) {
                val st = IntArray(8)
                for (g in s.stages) {
                    val m = Duration.between(g.startTime, g.endTime).toMinutes().toInt()
                    if (g.stage in 0..7) st[g.stage] += m
                }
                val j = JSONObject()
                j.put("s", s.startTime.toEpochMilli())
                j.put("e", s.endTime.toEpochMilli())
                j.put("off", (s.endZoneOffset ?: zone.rules.getOffset(s.endTime)).totalSeconds)
                j.put("src", s.metadata.dataOrigin.packageName)
                if (s.stages.isNotEmpty()) {
                    // 0 inconnu, 1 éveil, 2 sommeil, 3 hors du lit, 4 léger, 5 profond, 6 paradoxal, 7 éveillé au lit
                    j.put("awake", st[1] + st[3] + st[7])
                    j.put("light", st[4])
                    j.put("deep", st[5])
                    j.put("rem", st[6])
                    j.put("sleeping", st[2] + st[0])
                }
                sleep.put(j)
            }
        }
        o.put("sleep", sleep)

        // Pas : total par jour (Health Connect retire les doublons téléphone + montre)
        val steps = JSONObject()
        if (STEPS in granted) {
            val start = LocalDate.now(zone).minusDays(days - 1).atStartOfDay()
            val end = LocalDate.now(zone).plusDays(1).atStartOfDay()
            val res = hc.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
            for (r in res) {
                val n = r.result[StepsRecord.COUNT_TOTAL] ?: 0L
                if (n > 0L) steps.put(r.startTime.toLocalDate().toString(), n)
            }
        }
        o.put("steps", steps)

        // Poids : toutes les pesées des 90 derniers jours
        val weight = JSONArray()
        if (WEIGHT in granted) {
            for (w in readAll(hc, WeightRecord::class, now.minus(Duration.ofDays(90)), now)) {
                val j = JSONObject()
                j.put("t", w.time.toEpochMilli())
                j.put("off", (w.zoneOffset ?: zone.rules.getOffset(w.time)).totalSeconds)
                j.put("kg", Math.round(w.weight.inKilograms * 10.0) / 10.0)
                j.put("src", w.metadata.dataOrigin.packageName)
                weight.put(j)
            }
        }
        o.put("weight", weight)
        return o
    }
}
