package fr.kbo9.mycoachsante

import android.view.WindowManager
import android.webkit.JavascriptInterface
import androidx.health.connect.client.HealthConnectClient
import org.json.JSONObject

/** Fonctions natives appelées par la page (window.MCNative). */
class Bridge(private val a: MainActivity) {

    @JavascriptInterface
    fun version(): String = a.versionName()

    @JavascriptInterface
    fun keepAwake(on: Boolean) {
        a.runOnUiThread {
            if (on) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @JavascriptInterface
    fun theme(dark: Boolean) {
        a.runOnUiThread { a.theme(dark) }
    }

    @JavascriptInterface
    fun reload() {
        a.runOnUiThread { a.reload() }
    }

    /* ---------- Samsung Health (Health Connect) ---------- */
    @JavascriptInterface
    fun healthStatus(): String {
        val st = Health.status(a)
        val g = a.granted
        return JSONObject()
            .put("available", st == HealthConnectClient.SDK_AVAILABLE)
            .put("update", st == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
            .put("data", g.containsAll(Health.DATA_PERMS))
            .put("background", Health.BACKGROUND in g)
            .toString()
    }

    @JavascriptInterface
    fun healthAuthorize() {
        a.runOnUiThread { a.askHealth() }
    }

    @JavascriptInterface
    fun healthRead() {
        a.runOnUiThread { a.readHealth() }
    }

    /* ---------- sauvegarde cloud : partage du jeton avec la synchro en arrière-plan ---------- */
    @JavascriptInterface
    fun cloudGet(): String = JSONObject().put("token", Prefs.token(a)).put("repo", Prefs.repo(a)).toString()

    @JavascriptInterface
    fun cloudSet(token: String, repo: String) {
        if (token.isBlank() || !repo.contains("/")) Prefs.clearLink(a)
        else if (token != Prefs.token(a) || repo != Prefs.repo(a)) Prefs.setLink(a, token, repo)
        a.runOnUiThread { a.scheduleSync() }
    }

    /* ---------- fichiers ---------- */
    @JavascriptInterface
    fun shareFile(name: String, mime: String, b64: String, title: String) {
        a.runOnUiThread { a.shareFile(name, mime, b64, title, false) }
    }

    @JavascriptInterface
    fun openFile(name: String, mime: String, b64: String) {
        a.runOnUiThread { a.shareFile(name, mime, b64, "", true) }
    }

    @JavascriptInterface
    fun shareText(text: String, title: String) {
        a.runOnUiThread { a.shareText(text, title) }
    }

    /* ---------- notifications et voix ---------- */
    @JavascriptInterface
    fun restAlarm(at: Double, title: String, body: String) {
        Notifs.scheduleRest(a, at.toLong(), title, body)
    }

    @JavascriptInterface
    fun reminders(json: String) {
        Notifs.setReminders(a, json)
    }

    @JavascriptInterface
    fun audio(on: Boolean, title: String, seconds: Double) {
        a.runOnUiThread { a.audio(on, title, seconds.toLong()) }
    }

    /* ---------- voix naturelle (synthèse vocale d'Android) ---------- */
    @JavascriptInterface
    fun ttsOk(): Boolean = Coach.ok()

    @JavascriptInterface
    fun ttsVoices(): String = Coach.voicesJson()

    @JavascriptInterface
    fun ttsEngines(): String = Coach.enginesJson()

    @JavascriptInterface
    fun ttsSetVoice(name: String) {
        a.runOnUiThread { Coach.setVoice(name) }
    }

    @JavascriptInterface
    fun ttsSetEngine(pkg: String) {
        a.runOnUiThread { Coach.setEngine(pkg) }
    }

    @JavascriptInterface
    fun ttsSay(text: String) {
        a.runOnUiThread { Coach.say(text, true) }
    }

    @JavascriptInterface
    fun voicePlan(json: String) {
        a.runOnUiThread { Coach.plan(json) }
    }

    @JavascriptInterface
    fun voiceStop() {
        a.runOnUiThread { Coach.cancel() }
    }

    @JavascriptInterface
    fun ttsSettings() {
        a.runOnUiThread { a.openTtsSettings() }
    }

    /* ---------- ton planning (appli Planning dans My Coach) ---------- */
    @JavascriptInterface
    fun openPlanning() {
        a.runOnUiThread { a.openPlanning() }
    }

    @JavascriptInterface
    fun planningOk(): Boolean = true
}
