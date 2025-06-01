package com.marinov.colegioetapalegacy

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.webkit.CookieManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {
    private val tag = "SettingsActivity"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val iconSizeDp = 24 // Tamanho fixo para os ícones em dp

    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupToolbar()
        setupUI()
        if (intent.getBooleanExtra("open_update_directly", false)) {
            checkUpdate()
        }
    }

    private fun setupToolbar() {
        val btnBack = findViewById<ImageButton>(R.id.btn_back)

        // Configurar ação do botão de voltar
        btnBack.setOnClickListener {
            onBackPressed()
        }

        // Configurar tamanho do ícone de voltar
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)
        drawable?.let {
            // Definir tamanho fixo para o ícone (24dp)
            val sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                24f,
                resources.displayMetrics
            ).toInt()

            it.setBounds(0, 0, sizePx, sizePx)
            btnBack.setImageDrawable(it)
        }
    }

    private fun setupUI() {
        val btnCheck = findViewById<Button>(R.id.btn_check_update)
        val btnClear = findViewById<Button>(R.id.btn_clear_data)
        val btnClearPassword = findViewById<Button>(R.id.btn_clear_password)
        val btnTwitter = findViewById<Button>(R.id.btn_twitter)
        val btnReddit = findViewById<Button>(R.id.btn_reddit)
        val btnGithub = findViewById<Button>(R.id.btn_github)
        val btnYoutube = findViewById<Button>(R.id.btn_youtube)

        // Configurar ícones com tamanho fixo
        setButtonIcon(btnCheck, R.drawable.ic_update)
        setButtonIcon(btnClear, R.drawable.ic_delete_sweep)
        setButtonIcon(btnClearPassword, R.drawable.ic_security)
        setButtonIcon(btnTwitter, R.drawable.ic_twitter)
        setButtonIcon(btnReddit, R.drawable.ic_reddit)
        setButtonIcon(btnGithub, R.drawable.ic_github)
        setButtonIcon(btnYoutube, R.drawable.ic_youtube)

        btnTwitter.setOnClickListener { openUrl("http://x.com/gmb7886") }
        btnReddit.setOnClickListener { openUrl("https://www.reddit.com/user/GMB7886/") }
        btnGithub.setOnClickListener { openUrl("https://github.com/gmb7886/") }
        btnYoutube.setOnClickListener { openUrl("https://youtube.com/@CanalDoMarinov") }
        btnCheck.setOnClickListener { checkUpdate() }

        btnClear.setOnClickListener {
            clearAllCacheData()
            Toast.makeText(this, "Dados limpos com sucesso!", Toast.LENGTH_SHORT).show()
        }

        btnClearPassword.setOnClickListener {
            clearAutoFill()
            Toast.makeText(this, "Credenciais removidas!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setButtonIcon(button: Button, drawableResId: Int) {
        val drawable = ContextCompat.getDrawable(this, drawableResId)
        drawable?.let {
            // Converter dp para pixels
            val sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                iconSizeDp.toFloat(),
                resources.displayMetrics
            ).toInt()

            // Definir tamanho fixo
            it.setBounds(0, 0, sizePx, sizePx)

            // Aplicar ao botão
            button.setCompoundDrawables(it, null, null, null)

            // Definir padding entre ícone e texto
            button.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
        }
    }

    private fun clearAllCacheData() {
        listOf(
            "horarios_prefs",
            "calendario_prefs",
            "materia_cache",
            "notas_prefs",
            "HomeFragmentCache"
        ).forEach { clearSharedPreferences(it) }

        // Limpar cookies apenas se suportado
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    private fun clearAutoFill() {
        clearSharedPreferences("autofill_prefs")
    }

    private fun clearSharedPreferences(name: String) {
        getSharedPreferences(name, MODE_PRIVATE).edit { clear() }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e(tag, "Erro ao abrir URL", e)
            Toast.makeText(this, "Nenhum app para abrir link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUpdate() = coroutineScope.launch {
        try {
            val (json, responseCode) = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://api.github.com/repos/gmb7886/EtapaClient/releases/latest")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connection.setRequestProperty("User-Agent", "EtapaClient-Android")
                    connection.connectTimeout = 10000
                    connection.connect()

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            JSONObject(input.readText()) to connection.responseCode
                        }
                    } else {
                        null to connection.responseCode
                    }
                } catch (_: Exception) {
                    null to -1
                }
            }

            if (json != null) {
                processReleaseData(json)
            } else {
                showError("Falha na conexão: $responseCode")
            }
        } catch (e: Exception) {
            showError("Erro: ${e.message}")
        }
    }

    private fun InputStream.readText(): String {
        return BufferedReader(InputStreamReader(this)).use { it.readText() }
    }

    private fun processReleaseData(release: JSONObject) {
        val latest = release.getString("tag_name")
        if (latest == BuildConfig.VERSION_NAME) {
            showMessage()
        } else {
            val assets = release.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    promptForUpdate(asset.getString("browser_download_url"))
                    return
                }
            }
            showError("APK não encontrado no release")
        }
    }

    private fun promptForUpdate(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Atualização Disponível")
            .setMessage("Deseja baixar a versão mais recente?")
            .setPositiveButton("Sim") { _, _ -> startManualDownload(url) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun startManualDownload(apkUrl: String) {
        coroutineScope.launch {
            val progressDialog = createProgressDialog().apply { show() }
            try {
                val apkFile = withContext(Dispatchers.IO) { downloadApk(apkUrl) }
                progressDialog.dismiss()
                apkFile?.let(::showInstallDialog) ?: showError("Falha no download")
            } catch (e: Exception) {
                progressDialog.dismiss()
                showError("Erro: ${e.message}")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun createProgressDialog(): AlertDialog {
        val view = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        progressBar = view.findViewById(R.id.progress_bar)
        return AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
    }

    private suspend fun downloadApk(apkUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connect()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputDir = File(downloadsDir, "EtapaClient").apply { mkdirs() }
            val outputFile = File(outputDir, "app_release.apk")

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var total: Long = 0
                    val fileLength = connection.contentLength.toLong()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            withContext(Dispatchers.Main) {
                                progressBar?.progress = progress
                            }
                        }
                    }
                }
            }
            outputFile
        } catch (_: Exception) {
            null
        }
    }

    private fun showInstallDialog(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            AlertDialog.Builder(this)
                .setTitle("Download Concluído")
                .setMessage("Instalar agora?")
                .setPositiveButton("Instalar") { _, _ -> startActivity(installIntent) }
                .setNegativeButton("Cancelar", null)
                .show()
        } catch (e: Exception) {
            showError("Erro na instalação: ${e.message}")
        }
    }

    private fun showMessage() {
        AlertDialog.Builder(this)
            .setMessage("Você já tem a versão mais recente")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Erro")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}