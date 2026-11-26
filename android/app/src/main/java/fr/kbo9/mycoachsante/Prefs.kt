package fr.kbo9.mycoachsante

import android.content.Context
import android.content.SharedPreferences

/** Réglages locaux : jeton GitHub, dépôt, état de la dernière synchro. */
object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("mcs", Context.MODE_PRIVATE)

    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun repo(c: Context): String = sp(c).getString("repo", "") ?: ""
    fun linked(c: Context): Boolean = token(c).isNotEmpty() && repo(c).isNotEmpty()

    fun setLink(c: Context, token: String, repo: String) {
        sp(c).edit().putString("token", token).putString("repo", repo).remove("sha").apply()
    }

    fun clearLink(c: Context) {
        sp(c).edit().remove("token").remove("repo").remove("sha").apply()
    }

    fun sha(c: Context): String? = sp(c).getString("sha", null)
    fun setSha(c: Context, s: String?) { sp(c).edit().putString("sha", s).apply() }

    fun lastOk(c: Context): Long = sp(c).getLong("lastOk", 0L)
    fun summary(c: Context): String = sp(c).getString("summary", "") ?: ""
    fun error(c: Context): String = sp(c).getString("err", "") ?: ""

    fun ok(c: Context, summary: String) {
        sp(c).edit().putLong("lastOk", System.currentTimeMillis()).putString("summary", summary).putString("err", "").apply()
    }

    fun fail(c: Context, err: String) { sp(c).edit().putString("err", err).apply() }
}
