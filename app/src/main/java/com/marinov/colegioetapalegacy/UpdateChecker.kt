package com.marinov.colegioetapalegacy

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.stream.Collectors
import androidx.core.content.edit

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val PREFS_NAME = "UpdatePrefs"
    private const val KEY_LAST_VERSION = "last_version"

    interface UpdateListener {
        fun onUpdateAvailable(url: String)
        fun onUpToDate()
        fun onError(message: String)
    }

    fun checkForUpdate(context: Context, listener: UpdateListener) {
        Thread {
            runCatching {
                val url = URL("https://api.github.com/repos/gmb7886/EtapaClient/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "EtapaClient-Android")
                    connectTimeout = 10000
                }

                when (conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val json = readResponseStream(conn)
                        val release = JSONObject(json)
                        val latestVersion = release.getString("tag_name")
                        val currentVersion = BuildConfig.VERSION_NAME

                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val lastNotifiedVersion = prefs.getString(KEY_LAST_VERSION, "") ?: ""

                        when {
                            latestVersion != currentVersion && latestVersion != lastNotifiedVersion -> {
                                prefs.edit { putString(KEY_LAST_VERSION, latestVersion) }
                                listener.onUpdateAvailable(release.getString("html_url"))
                            }
                            else -> listener.onUpToDate()
                        }
                    }
                    else -> listener.onError("HTTP error: ${conn.responseCode}")
                }
            }.onFailure { e ->
                Log.e(TAG, "Erro na verificação", e)
                listener.onError(e.message ?: "Erro desconhecido")
            }
        }.start()
    }

    private fun readResponseStream(conn: HttpURLConnection): String {
        return conn.inputStream.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    reader.lines().collect(Collectors.joining("\n"))
                } else {
                    TODO("VERSION.SDK_INT < N")
                }
            }
        }
    }
}