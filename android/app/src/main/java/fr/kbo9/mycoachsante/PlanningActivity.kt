package fr.kbo9.mycoachsante

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Ton appli Planning, dans My Coach : même site, même stockage.
 * Chaque changement fait ici est vu tout de suite par My Coach
 * (séances, repas et rappels recalculés).
 */
class PlanningActivity : ComponentActivity() {
    companion object {
        const val URL = "https://kbo9.github.io/planning/"
    }

    private lateinit var web: WebView
    private var offline = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cream = Color.rgb(244, 241, 234)
        window.statusBarColor = cream
        window.navigationBarColor = Color.rgb(255, 253, 248)
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.isAppearanceLightStatusBars = true
        ctl.isAppearanceLightNavigationBars = true

        web = WebView(this)
        web.setBackgroundColor(cream)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = 100
        s.setSupportZoom(false)
        s.allowFileAccess = false
        s.userAgentString = s.userAgentString + " MyCoachApp/" + version()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handleUrl(request.url)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showOffline()
            }
        }
        // « Copié ! », « Tout réinitialiser ? »… : les fenêtres de Planning s'affichent
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(PlanBridge(this), "MCPlanning")
        setContentView(web)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
            }
        })
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(URL)
    }

    override fun onPause() {
        // en quittant le planning : My Coach vérifie tout de suite s'il a changé
        MainActivity.poke()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        MainActivity.poke()
        super.onDestroy()
    }

    private fun handleUrl(u: Uri): Boolean {
        val scheme = u.scheme ?: return false
        val path = u.path ?: ""
        if ((scheme == "https" || scheme == "http") && u.host == "kbo9.github.io") {
            if (path.startsWith("/planning")) return false
            if (path.startsWith("/my-coach")) {
                openCoach()
                return true
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u))
        } catch (e: Exception) {
            // aucune appli pour ce lien
        }
        return true
    }

    fun openCoach() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun reload() {
        offline = false
        web.loadUrl(URL)
    }

    private fun version(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun showOffline() {
        if (offline) return
        offline = true
        val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='background:#f4f1ea;color:#1c1a17;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px;box-sizing:border-box;text-align:center'>" +
            "<h2>Pas de connexion</h2><p style='color:#5a554c;line-height:1.4'>Connecte-toi une première fois pour charger ton planning. Ensuite, il marche aussi hors connexion.</p>" +
            "<button onclick='MCPlanning.reload()' style='margin-top:18px;background:#1c1a17;color:#f4f1ea;border:0;border-radius:24px;padding:14px 30px;font-size:17px'>Réessayer</button></body></html>"
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}

/** Fonctions natives de l'écran Planning (window.MCPlanning). */
class PlanBridge(private val a: PlanningActivity) {
    @android.webkit.JavascriptInterface
    fun reload() {
        a.runOnUiThread { a.reload() }
    }

    @android.webkit.JavascriptInterface
    fun openCoach() {
        a.runOnUiThread { a.openCoach() }
    }
}
