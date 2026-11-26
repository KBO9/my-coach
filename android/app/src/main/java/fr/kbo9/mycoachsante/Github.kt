package fr.kbo9.mycoachsante

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Envoi du fichier sante.json dans le dépôt privé (API GitHub « contents »). */
object Github {
    class Resp(val code: Int, val body: String)

    private fun call(method: String, url: String, token: String, body: String?): Resp {
        val cn = URL(url).openConnection() as HttpURLConnection
        try {
            cn.requestMethod = method
            cn.connectTimeout = 20000
            cn.readTimeout = 30000
            cn.setRequestProperty("Accept", "application/vnd.github+json")
            cn.setRequestProperty("Authorization", "Bearer $token")
            cn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            cn.setRequestProperty("User-Agent", "MyCoachSante")
            if (body != null) {
                cn.doOutput = true
                cn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                cn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = cn.responseCode
            val stream = if (code in 200..299) cn.inputStream else cn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            cn.disconnect()
        }
    }

    private fun contentsUrl(repo: String, path: String) = "https://api.github.com/repos/$repo/contents/$path"

    fun explain(r: Resp): String = when (r.code) {
        401 -> "Jeton refusé ou expiré"
        403 -> "Accès refusé : le jeton doit avoir Contents en lecture et écriture"
        404 -> "Dépôt introuvable, ou le jeton n’y a pas accès"
        else -> "Erreur GitHub " + r.code
    }

    fun currentSha(token: String, repo: String, path: String): String? {
        val r = call("GET", contentsUrl(repo, path), token, null)
        if (r.code == 404) return null
        if (r.code !in 200..299) throw IOException(explain(r))
        val sha = JSONObject(r.body).optString("sha")
        return if (sha.isEmpty()) null else sha
    }

    /** Crée ou remplace le fichier ; renvoie son nouveau sha. */
    fun put(token: String, repo: String, path: String, text: String, message: String, knownSha: String?): String {
        var sha = knownSha
        for (attempt in 0..2) {
            val b = JSONObject()
            b.put("message", message)
            b.put("content", Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            if (sha != null) b.put("sha", sha)
            val r = call("PUT", contentsUrl(repo, path), token, b.toString())
            if (r.code in 200..299) return JSONObject(r.body).getJSONObject("content").getString("sha")
            if (r.code == 409 || r.code == 422) {
                sha = currentSha(token, repo, path)
                continue
            }
            throw IOException(explain(r))
        }
        throw IOException("Conflit de version sur GitHub : réessaie")
    }

    /** null si tout va bien, sinon un message lisible. */
    fun check(token: String, repo: String): String? {
        val r = call("GET", "https://api.github.com/repos/$repo", token, null)
        if (r.code == 200) {
            return if (JSONObject(r.body).optBoolean("private", false)) null
            else "Ce dépôt est public : choisis un dépôt privé pour tes données"
        }
        return explain(r)
    }
}
