package fr.kbo9.mycoachsante

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Voix du coach : synthèse vocale d'Android (voix naturelles de Google ou de Samsung).
 * Les annonces sont programmées à l'avance et continuent écran verrouillé ;
 * la musique d'une autre appli baisse le temps de la phrase au lieu de s'arrêter.
 */
object Coach {
    private var tts: TextToSpeech? = null
    /** Moteur démarré. */
    @Volatile var ready = false
    /** Au moins une voix française installée. */
    @Volatile var french = false
    var onReady: (() -> Unit)? = null
    private var app: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pending = ArrayList<Runnable>()
    private var wake: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private const val SR = 24000
    private val beepShort: ShortArray by lazy { tone(880.0, 110, 0.5) }
    private val beepLong: ShortArray by lazy { tone(1320.0, 420, 0.55) }
    private val femaleRx = Regex("(smt|[-_#])f\\d\\d|-x-fr[ac]-")
    private val maleRx = Regex("(smt|[-_#])m\\d\\d|-x-fr[bd]-")

    private fun prefs(c: Context) = c.getSharedPreferences("mcs", Context.MODE_PRIVATE)

    fun ok(): Boolean = ready && french

    fun init(c: Context) {
        if (app == null) app = c.applicationContext
        if (tts == null) start()
    }

    /** Au retour dans l'appli : une voix française vient peut-être d'être installée. */
    fun refresh() {
        if (app == null) return
        if (tts == null) start() else if (ready && !french) handler.post { configure() }
    }

    private fun start() {
        val c = app ?: return
        ready = false
        val engine = prefs(c).getString("ttsEngine", null)
        tts = TextToSpeech(c, { st -> handler.post { if (st == TextToSpeech.SUCCESS) configure() } }, engine)
    }

    private fun configure() {
        val t = tts ?: return
        val c = app ?: return
        val lang = try { t.setLanguage(Locale.FRANCE) } catch (e: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
        try { t.setAudioAttributes(attrs) } catch (e: Exception) { }
        val vs = voices()
        french = vs.isNotEmpty() || lang >= TextToSpeech.LANG_AVAILABLE
        val saved = prefs(c).getString("ttsVoice", null)
        // voices() met en tête les voix d'homme hors ligne
        val v = vs.firstOrNull { it.name == saved } ?: vs.firstOrNull()
        if (v != null) {
            try { t.setVoice(v) } catch (e: Exception) { }
        }
        // vitesse et hauteur d'origine : c'est là que la voix est la plus naturelle
        t.setSpeechRate(1.0f)
        t.setPitch(1.0f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.postDelayed({ if (tts?.isSpeaking != true) releaseFocus() }, 300)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { releaseFocus() }
            }
        })
        ready = true
        onReady?.invoke()
    }

    /** "m" voix d'homme, "f" voix de femme, "" inconnu. */
    fun gender(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.contains("female") || n.contains("femme") || femaleRx.containsMatchIn(n) -> "f"
            n.contains("male") || n.contains("homme") || maleRx.containsMatchIn(n) -> "m"
            else -> ""
        }
    }

    /** Voix françaises installées : France, voix d'homme, hors ligne et qualité d'abord. */
    fun voices(): List<Voice> {
        val t = tts ?: return emptyList()
        return try {
            (t.voices ?: emptySet<Voice>())
                .filter { it.locale.language == "fr" && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                .sortedWith(compareBy<Voice>(
                    { it.locale.country != "FR" },
                    { when (gender(it.name)) { "m" -> 0; "" -> 1; else -> 2 } },
                    { it.isNetworkConnectionRequired },
                    { -it.quality },
                    { it.name }
                ))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun voicesJson(): String {
        val cur = try { tts?.voice?.name } catch (e: Exception) { null }
        val a = JSONArray()
        for (v in voices()) {
            a.put(
                JSONObject().put("name", v.name).put("net", v.isNetworkConnectionRequired).put("q", v.quality)
                    .put("cur", v.name == cur).put("country", v.locale.country).put("g", gender(v.name))
            )
        }
        return JSONObject().put("ready", ready).put("french", french).put("voices", a).toString()
    }

    fun enginesJson(): String {
        val c = app
        val a = JSONArray()
        try {
            for (e in tts?.engines ?: emptyList<TextToSpeech.EngineInfo>()) a.put(JSONObject().put("name", e.name).put("label", e.label))
        } catch (e: Exception) { }
        val cur = (if (c != null) prefs(c).getString("ttsEngine", null) else null) ?: (try { tts?.defaultEngine } catch (e: Exception) { null }) ?: ""
        return JSONObject().put("cur", cur).put("engines", a).toString()
    }

    fun setVoice(name: String) {
        val c = app ?: return
        prefs(c).edit().putString("ttsVoice", name).apply()
        val v = voices().firstOrNull { it.name == name } ?: return
        try { tts?.setVoice(v) } catch (e: Exception) { }
    }

    fun setEngine(pkg: String) {
        val c = app ?: return
        prefs(c).edit().putString("ttsEngine", pkg).remove("ttsVoice").apply()
        cancel()
        try { tts?.shutdown() } catch (e: Exception) { }
        tts = null
        french = false
        start()
    }

    fun say(text: String, flush: Boolean) {
        val t = tts ?: return
        if (!ok() || text.isBlank()) return
        holdFocus()
        t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "mc" + System.nanoTime())
    }

    /** Bip doux (sinusoïde), le même que dans l'appli web. */
    private fun tone(freq: Double, ms: Int, amp: Double): ShortArray {
        val n = SR * ms / 1000
        val d = ms / 1000.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / SR
            val env = min(1.0, min(t / 0.008, (d - t) / 0.03)).coerceAtLeast(0.0)
            (amp * env * sin(2 * PI * freq * t) * 32767).toInt().toShort()
        }
    }

    private fun beep(kind: Int) {
        try {
            val data = if (kind >= 2) beepLong else beepShort
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            holdFocus()
            track.play()
            handler.postDelayed({
                try { track.release() } catch (e: Exception) { }
                if (tts?.isSpeaking != true) releaseFocus()
            }, data.size * 1000L / SR + 500L)
        } catch (e: Exception) { }
    }

    private fun post(delay: Long, f: () -> Unit) {
        val r = Runnable { f() }
        pending.add(r)
        handler.postDelayed(r, delay.coerceAtLeast(0L))
    }

    /** json : [{ t: secondes à partir de maintenant, s: "texte", b: 0 rien, 1 bip, 2 bip long }] */
    fun plan(json: String) {
        cancel()
        val arr = try { JSONArray(json) } catch (e: Exception) { return }
        var maxT = 0.0
        for (n in 0 until arr.length()) {
            val o = arr.optJSONObject(n) ?: continue
            val t = o.optDouble("t", 0.0)
            val s = o.optString("s", "")
            val b = o.optInt("b", 0)
            if (t > maxT) maxT = t
            val at = (t * 1000).toLong()
            if (b > 0) post(at) { beep(b) }
            // la phrase part juste après le bip
            if (s.isNotEmpty()) post(at + (if (b >= 2) 450L else if (b == 1) 180L else 0L)) { say(s, false) }
        }
        val ms = ((maxT + 20) * 1000).toLong()
        acquireWake(ms)
        post(ms) { releaseWake() }
    }

    fun cancel() {
        for (r in pending) handler.removeCallbacks(r)
        pending.clear()
        try { tts?.stop() } catch (e: Exception) { }
        releaseFocus()
        releaseWake()
    }

    private fun holdFocus() {
        if (focus != null) return
        val am = app?.getSystemService(AudioManager::class.java) ?: return
        val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        if (am.requestAudioFocus(f) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = f
    }

    private fun releaseFocus() {
        val f = focus ?: return
        try { app?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(f) } catch (e: Exception) { }
        focus = null
    }

    private fun acquireWake(ms: Long) {
        releaseWake()
        val pm = app?.getSystemService(PowerManager::class.java) ?: return
        try {
            val w = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyCoach:voix")
            w.acquire(ms)
            wake = w
        } catch (e: Exception) { }
    }

    private fun releaseWake() {
        try { wake?.let { if (it.isHeld) it.release() } } catch (e: Exception) { }
        wake = null
    }
}
