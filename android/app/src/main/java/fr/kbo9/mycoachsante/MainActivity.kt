package fr.kbo9.mycoachsante

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference

/**
 * My Coach pour Android : l'appli web (toujours à jour, depuis GitHub Pages)
 * + ce qu'une page web ne sait pas faire : Samsung Health, notifications,
 * voix du coach écran verrouillé, partage de fichiers.
 */
class MainActivity : ComponentActivity() {
    companion object {
        const val HOME = "https://kbo9.github.io/my-coach/"
        @Volatile var visible = false
        private var live: WeakReference<MainActivity>? = null

        /** Le planning vient peut-être de changer : My Coach recalcule séances, repas et rappels. */
        fun poke() {
            live?.get()?.js("window.mcPlanning && mcPlanning()")
        }
    }

    lateinit var web: WebView
    @Volatile var granted: Set<String> = emptySet()
    private lateinit var askPerms: ActivityResultLauncher<Set<String>>
    private lateinit var pickFile: ActivityResultLauncher<Array<String>>
    private lateinit var askNotif: ActivityResultLauncher<String>
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var offline = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        live = WeakReference(this)
        askPerms = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { g ->
            granted = g
            val ok = g.containsAll(Health.DATA_PERMS)
            js("window.mcHealthAuth && mcHealthAuth(" + ok + ")")
            if (ok) {
                readHealth()
                scheduleSync()
            }
        }
        pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            fileCb?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            fileCb = null
        }
        askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        Notifs.channels(this)
        Coach.onReady = { js("window.mcVoices && mcVoices()") }
        Coach.init(this)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        web = WebView(this)
        web.setBackgroundColor(if (night) Color.BLACK else Color.rgb(242, 242, 247))
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = 100
        s.setSupportZoom(false)
        s.allowFileAccess = false
        s.userAgentString = s.userAgentString + " MyCoachApp/" + versionName()
        web.addJavascriptInterface(Bridge(this), "MCNative")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handleUrl(request.url)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showOffline()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = filePathCallback
                return try {
                    pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    true
                } catch (e: Exception) {
                    fileCb = null
                    false
                }
            }
        }
        setContentView(web)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.mcBack && mcBack()) ? 'oui' : 'non'") { r ->
                    if (r == null || !r.contains("oui")) moveTaskToBack(true)
                }
            }
        })

        handleLink(intent)
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(HOME)

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val p = getSharedPreferences("mcs", MODE_PRIVATE)
            if (!p.getBoolean("notifAsked", false)) {
                p.edit().putBoolean("notifAsked", true).apply()
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Notifs.scheduleReminders(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Coach.refresh()
        lifecycleScope.launch {
            granted = try {
                if (Health.available(this@MainActivity)) Health.granted(this@MainActivity) else emptySet()
            } catch (e: Exception) {
                emptySet()
            }
            js("window.mcResume && mcResume()")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        if (live?.get() === this) live = null
        super.onDestroy()
    }

    /** Ton planning, dans l'appli (même stockage que My Coach). */
    fun openPlanning() {
        try {
            startActivity(Intent(this, PlanningActivity::class.java))
        } catch (e: Exception) {
            js("window.toast && toast('Impossible d’ouvrir le planning', 'x', 'var(--red)')")
        }
    }

    /** Liens : My Coach reste dans l'appli, le reste s'ouvre dans l'appli adaptée. */
    private fun handleUrl(u: Uri): Boolean {
        val scheme = u.scheme ?: return false
        if (scheme == "intent") {
            try {
                startActivity(Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME))
            } catch (e: Exception) {
                // appli absente : rien à faire
            }
            return true
        }
        if (scheme == "mycoachsante") {
            handleLink(Intent(Intent.ACTION_VIEW, u))
            return true
        }
        if ((scheme == "https" || scheme == "http") && u.host == "kbo9.github.io" && (u.path ?: "").startsWith("/my-coach")) return false
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u))
        } catch (e: Exception) {
            // aucune appli pour ce lien
        }
        return true
    }

    /** Lien de transfert envoyé depuis My Coach (navigateur) : mycoachsante://setup?t=JETON&r=pseudo/depot */
    private fun handleLink(i: Intent?) {
        if (i == null) return
        val u = i.data ?: return
        if (u.scheme != "mycoachsante") return
        val t = (u.getQueryParameter("t") ?: "").trim()
        val r = (u.getQueryParameter("r") ?: "").trim()
        i.data = null
        if (t.length >= 20 && r.contains("/")) {
            Prefs.setLink(this, t, r)
            js("window.mcCloud && mcCloud()")
            scheduleSync()
        }
    }

    fun js(code: String) {
        runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(code, null) }
    }

    fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    fun askHealth() {
        val st = Health.status(this)
        if (st == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Health.PROVIDER)))
            } catch (e: Exception) {
                // pas de Play Store
            }
            return
        }
        if (st != HealthConnectClient.SDK_AVAILABLE) {
            js("window.toast && toast('Health Connect n’est pas disponible sur ce téléphone', 'info', 'var(--orange)')")
            return
        }
        try {
            askPerms.launch(Health.ALL_PERMS)
        } catch (e: Exception) {
            js("window.toast && toast('Impossible d’ouvrir Health Connect', 'x', 'var(--red)')")
        }
    }

    /** Lit Samsung Health (via Health Connect) et le donne à la page. */
    fun readHealth() {
        lifecycleScope.launch {
            val json = try {
                if (Health.available(this@MainActivity)) {
                    granted = Health.granted(this@MainActivity)
                    if (granted.containsAll(Health.DATA_PERMS)) Health.collect(this@MainActivity).toString() else "null"
                } else "null"
            } catch (e: Exception) {
                "null"
            }
            js("window.mcHealth && mcHealth($json)")
        }
    }

    fun scheduleSync() {
        if (Prefs.linked(this)) Sync.schedule(this)
    }

    fun shareFile(name: String, mime: String, b64: String, title: String, view: Boolean) {
        try {
            val dir = File(cacheDir, "partage")
            dir.mkdirs()
            val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            val uri = FileProvider.getUriForFile(this, "$packageName.fichiers", f)
            val i = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            else Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(if (view) i else Intent.createChooser(i, title))
        } catch (e: Exception) {
            js("window.toast && toast('Impossible d’ouvrir ce fichier', 'x', 'var(--red)')")
        }
    }

    fun shareText(text: String, title: String) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title))
        } catch (e: Exception) {
            // aucune appli de partage
        }
    }

    fun audio(on: Boolean, title: String, seconds: Long) {
        val i = Intent(this, AudioService::class.java)
        if (on) {
            i.putExtra("t", title).putExtra("s", seconds)
            try {
                ContextCompat.startForegroundService(this, i)
            } catch (e: Exception) {
                // démarrage refusé en arrière-plan : la voix continue sans le service
            }
        } else {
            stopService(i)
        }
    }

    /** Couleur des barres système selon le thème choisi dans My Coach. */
    fun theme(dark: Boolean) {
        val c = if (dark) Color.BLACK else Color.rgb(242, 242, 247)
        window.statusBarColor = c
        window.navigationBarColor = c
        web.setBackgroundColor(c)
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.isAppearanceLightStatusBars = !dark
        ctl.isAppearanceLightNavigationBars = !dark
    }

    fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            js("window.toast && toast('Ouvre Paramètres → Gestion globale → Synthèse vocale', 'info', 'var(--orange)')")
        }
    }

    fun reload() {
        offline = false
        web.loadUrl(HOME)
    }

    private fun showOffline() {
        if (offline) return
        offline = true
        val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='background:#000;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px;box-sizing:border-box;text-align:center'>" +
            "<h2>Pas de connexion</h2><p style='color:#aaa;line-height:1.4'>Connecte-toi une première fois pour charger My Coach. Ensuite, l’appli marche aussi hors connexion.</p>" +
            "<button onclick='MCNative.reload()' style='margin-top:18px;background:#FF375F;color:#fff;border:0;border-radius:24px;padding:14px 30px;font-size:17px'>Réessayer</button></body></html>"
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
