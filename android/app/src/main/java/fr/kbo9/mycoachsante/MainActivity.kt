package fr.kbo9.mycoachsante

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var askPerms: ActivityResultLauncher<Set<String>>
    private var granted: Set<String> = emptySet()
    private var busy = false

    private val pink = 0xFFFF375F.toInt()
    private val cardBg = 0xFF1C1C1E.toInt()
    private val fieldBg = 0xFF2C2C2E.toInt()
    private val green = 0xFF30D158.toInt()
    private val orange = 0xFFFF9F0A.toInt()
    private val dim = 0x9EEBEBF5.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPerms = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { g ->
            granted = g
            render()
            if (g.containsAll(Health.DATA_PERMS) && Prefs.linked(this)) {
                Sync.schedule(this)
                syncNow("premier accès")
            }
        }
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(40), dp(20), dp(36))
        val sc = ScrollView(this)
        sc.setBackgroundColor(Color.BLACK)
        sc.isFillViewport = true
        sc.addView(root)
        setContentView(sc)
        handleLink(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Lien envoyé par My Coach : mycoachsante://setup?t=JETON&r=pseudo/depot */
    private fun handleLink(i: Intent?) {
        if (i == null) return
        val u = i.data ?: return
        if (u.scheme != "mycoachsante") return
        val t = (u.getQueryParameter("t") ?: "").trim()
        val r = (u.getQueryParameter("r") ?: "").trim()
        i.data = null
        if (t.length < 20 || !r.contains("/")) {
            toast("Lien de liaison incomplet")
            return
        }
        Prefs.setLink(this, t, r)
        toast("Relié à $r")
    }

    private fun refresh() {
        if (!Health.available(this)) {
            granted = emptySet()
            render()
            return
        }
        lifecycleScope.launch {
            granted = try { Health.granted(this@MainActivity) } catch (e: Exception) { emptySet() }
            render()
            if (granted.containsAll(Health.DATA_PERMS) && Prefs.linked(this@MainActivity)) {
                Sync.schedule(this@MainActivity)
                if (System.currentTimeMillis() - Prefs.lastOk(this@MainActivity) > 30 * 60 * 1000L) syncNow("ouverture")
            }
        }
    }

    private fun syncNow(why: String) {
        if (busy) return
        busy = true
        render()
        lifecycleScope.launch {
            try {
                val s = Sync.run(this@MainActivity, why)
                toast("Synchronisé : $s")
            } catch (e: Exception) {
                val msg = if (e is SecurityException) "Accès refusé par Health Connect (étape 1)" else (e.message ?: "Erreur pendant la synchro")
                Prefs.fail(this@MainActivity, msg)
                toast(msg)
            }
            busy = false
            render()
        }
    }

    private fun link(tokenIn: String, repoIn: String) {
        val t = tokenIn.trim()
        val r = repoIn.trim().removePrefix("https://github.com/").trimEnd('/')
        if (t.length < 20 || !r.contains("/")) {
            toast("Jeton ou dépôt incomplet")
            return
        }
        lifecycleScope.launch {
            val err = withContext(Dispatchers.IO) {
                try { Github.check(t, r) } catch (e: Exception) { "Pas de réseau : réessaie" }
            }
            if (err != null) {
                toast(err)
                return@launch
            }
            Prefs.setLink(this@MainActivity, t, r)
            toast("Relié à $r")
            refresh()
        }
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text("My Coach Santé", 30f, Color.WHITE, true))
        root.addView(text("Envoie ton sommeil, tes pas et tes pesées de Samsung Health vers My Coach, tout seul, toutes les 2 heures.", 15f, dim, false), lp(6))

        val st = Health.status(this)
        val hcOk = st == HealthConnectClient.SDK_AVAILABLE
        val dataOk = hcOk && granted.containsAll(Health.DATA_PERMS)
        val bgOk = Health.BACKGROUND in granted
        val linked = Prefs.linked(this)

        val sub1 = when {
            st == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect doit être mis à jour."
            !hcOk -> "Health Connect n’est pas disponible sur ce téléphone."
            dataOk && bgOk -> "Autorisé : sommeil, pas et poids, y compris en arrière-plan."
            dataOk -> "Autorisé : sommeil, pas et poids. Autorise aussi l’accès en arrière-plan pour la synchro automatique."
            else -> "Autorise la lecture du sommeil, des pas et du poids."
        }
        val c1 = card("1", "Accès à tes données santé", sub1, dataOk && bgOk)
        if (st == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            c1.addView(button("Mettre à jour Health Connect", true) { openUrl("market://details?id=" + Health.PROVIDER) }, lp(12))
        } else if (hcOk && !(dataOk && bgOk)) {
            c1.addView(button(if (dataOk) "Autoriser l’arrière-plan" else "Autoriser", true) { askPerms.launch(Health.ALL_PERMS) }, lp(12))
        }

        val c2 = card("2", "Samsung Health → Health Connect", "Dans Samsung Health : Paramètres → Health Connect, puis autorise le sommeil, les pas et le poids.", false)
        c2.addView(button("Ouvrir Samsung Health", false) { openApp("com.sec.android.app.shealth") }, lp(12))

        val sub3 = if (linked) "Relié à " + Prefs.repo(this) + "." else "Le plus simple : dans My Coach, Réglages → Samsung Health → « Relier l’appli Santé ». Sinon, colle ici ton jeton GitHub."
        val c3 = card("3", "Liaison avec My Coach", sub3, linked)
        if (linked) {
            c3.addView(button("Délier", false) { Prefs.clearLink(this); render() }, lp(12))
        } else {
            val tok = edit("github_pat_…", "", true)
            val rep = edit("pseudo/my-coach-data", "KBO9/my-coach-data", false)
            c3.addView(tok, lp(12))
            c3.addView(rep, lp(8))
            c3.addView(button("Relier", true) { link(tok.text.toString(), rep.text.toString()) }, lp(10))
        }

        val last = Prefs.lastOk(this)
        val info = if (last > 0) "Dernière synchro : " + SimpleDateFormat("d MMM 'à' HH:mm", Locale.FRANCE).format(Date(last)) + "\n" + Prefs.summary(this) else "Pas encore de synchro."
        root.addView(text(info, 14f, dim, false), lp(22))
        val err = Prefs.error(this)
        if (err.isNotEmpty()) root.addView(text(err, 14f, orange, false), lp(6))
        root.addView(button(if (busy) "Synchronisation…" else "Synchroniser maintenant", true, !busy && dataOk && linked) { syncNow("manuel") }, lp(14))
        root.addView(button("Ouvrir My Coach", false) { openUrl("https://kbo9.github.io/my-coach/") }, lp(10))
        root.addView(button("Réglages Health Connect", false) { openHcSettings() }, lp(10))
        root.addView(text("Tes données vont uniquement dans ton dépôt GitHub privé.", 12f, 0x66EBEBF5, false), lp(18))
    }

    private fun card(num: String, title: String, sub: String, done: Boolean): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = round(cardBg, 22)
        c.setPadding(dp(16), dp(16), dp(16), dp(16))
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val badge = TextView(this)
        badge.text = if (done) "✓" else num
        badge.gravity = Gravity.CENTER
        badge.setTextColor(Color.WHITE)
        badge.setTypeface(Typeface.DEFAULT_BOLD)
        badge.textSize = 14f
        badge.background = round(if (done) green else 0xFF3A3A3C.toInt(), 14)
        head.addView(badge, LinearLayout.LayoutParams(dp(28), dp(28)))
        val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tp.leftMargin = dp(12)
        head.addView(text(title, 17f, Color.WHITE, true), tp)
        c.addView(head)
        c.addView(text(sub, 14f, dim, false), lp(8))
        root.addView(c, lp(16))
        return c
    }

    private fun dp(v: Int): Int = Math.round(v * resources.displayMetrics.density)

    private fun lp(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun round(color: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD)
        t.setLineSpacing(0f, 1.15f)
        return t
    }

    private fun button(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 16f
        b.setTypeface(Typeface.DEFAULT_BOLD)
        b.setTextColor(if (primary) Color.WHITE else pink)
        b.background = round(if (primary) pink else fieldBg, 16)
        b.stateListAnimator = null
        b.setPadding(dp(16), dp(14), dp(16), dp(14))
        b.isEnabled = enabled
        b.alpha = if (enabled) 1f else 0.45f
        b.setOnClickListener { onClick() }
        return b
    }

    private fun edit(hint: String, value: String, mono: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(0x66EBEBF5)
        e.textSize = 15f
        e.setSingleLine(true)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (mono) e.typeface = Typeface.MONOSPACE
        e.background = round(fieldBg, 12)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun openUrl(u: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: ActivityNotFoundException) {
            toast("Impossible d’ouvrir ce lien")
        }
    }

    private fun openApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i) else toast("Samsung Health n’est pas installé")
    }

    private fun openHcSettings() {
        val action = if (Build.VERSION.SDK_INT >= 34) "android.health.connect.action.HEALTH_HOME_SETTINGS" else "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            toast("Ouvre Health Connect depuis les Paramètres du téléphone")
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }
}
