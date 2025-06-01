package com.marinov.colegioetapalegacy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView

class MateriadeProva : Fragment() {

    companion object {
        private const val ARG_URL = "url_prova"
        private const val CACHE_PREFS = "materia_cache"
        private const val CACHE_KEY_PREFIX = "materia_"

        fun newInstance(url: String): MateriadeProva {
            return MateriadeProva().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }

    private lateinit var barraCompartilhamento: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var txtErro: TextView
    private lateinit var txtTitulo: TextView
    private lateinit var txtConteudo: TextView
    private lateinit var cache: CacheHelper
    private lateinit var currentUrl: String

    private lateinit var btnWhatsapp: ImageButton
    private lateinit var btnWechat: ImageButton
    private lateinit var btnChatgpt: ImageButton
    private lateinit var btnDeepseek: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_materia_prova, container, false)

        barraCompartilhamento = root.findViewById(R.id.barra_compartilhamento)
        progressBar = root.findViewById(R.id.progress_circular)
        txtErro = root.findViewById(R.id.txt_erro)
        txtTitulo = root.findViewById(R.id.txt_titulo)
        txtConteudo = root.findViewById(R.id.txt_conteudo)

        btnWhatsapp = root.findViewById(R.id.btn_whatsapp)
        btnWechat = root.findViewById(R.id.btn_wechat)
        btnChatgpt = root.findViewById(R.id.btn_chatgpt)
        btnDeepseek = root.findViewById(R.id.btn_deepseek)

        cache = CacheHelper(requireContext())
        currentUrl = arguments?.getString(ARG_URL) ?: ""

        carregarConteudo()
        configurarAcoesCompartilhamento()

        return root
    }

    private fun configurarAcoesCompartilhamento() {
        btnWhatsapp.setOnClickListener { compartilharConteudo("com.whatsapp") }
        btnWechat.setOnClickListener { compartilharConteudo("com.tencent.mm") }
        btnChatgpt.setOnClickListener { compartilharConteudo("com.openai.chatgpt") }
        btnDeepseek.setOnClickListener {
            Toast.makeText(requireContext(), "Não suportado no momento.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartilharConteudo(pacoteApp: String) {
        try {
            val texto = "${txtTitulo.text}\n\n${txtConteudo.text}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, texto)
                `package` = pacoteApp
            }

            if (isAppInstalled(pacoteApp)) {
                startActivity(intent)
            } else {
                val shareIntent = Intent.createChooser(intent, "Compartilhar via")
                startActivity(shareIntent)
            }
        } catch (e: Exception) {
            Log.e("Compartilhamento", "Erro: ${e.message}")
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            requireContext().packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun carregarConteudo() {
        val cachedData = cache.loadContent(currentUrl)
        if (cachedData != null) {
            exibirConteudoCache(cachedData)
        }

        if (isOnline()) {
            fetchContent()
        } else if (cachedData == null) {
            exibirErro("Não há dados.")
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.activeNetwork != null
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnectedOrConnecting == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun exibirConteudoCache(cachedData: String) {
        val parts = cachedData.split("|||", limit = 2)
        if (parts.size == 2) {
            txtTitulo.text = parts[0]
            val sp: Spanned = HtmlCompat.fromHtml(parts[1], HtmlCompat.FROM_HTML_MODE_LEGACY)
            txtConteudo.text = sp
            txtConteudo.setLineSpacing(8f, 1.0f)
            txtConteudo.movementMethod = android.text.method.LinkMovementMethod.getInstance()

            barraCompartilhamento.visibility = View.VISIBLE
            txtTitulo.visibility = View.VISIBLE
            txtConteudo.visibility = View.VISIBLE
            txtErro.visibility = View.GONE
        }
    }

    private fun fetchContent() {
        CoroutineScope(Dispatchers.Main).launch {
            exibirCarregando()

            try {
                val doc = withContext(Dispatchers.IO) {
                    try {
                        val cookieHeader = CookieManager.getInstance().getCookie(currentUrl)
                        Jsoup.connect(currentUrl)
                            .header("Cookie", cookieHeader)
                            .userAgent("Mozilla/5.0")
                            .timeout(15000)
                            .get()
                    } catch (e: Exception) {
                        Log.e("MateriadeProva", "Erro ao carregar conteúdo", e)
                        null
                    }
                }

                if (doc != null) {
                    processarDocumento(doc)
                } else {
                    verificarCacheFallback()
                }
            } catch (e: Exception) {
                Log.e("MateriadeProva", "Erro inesperado", e)
                verificarCacheFallback()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun exibirCarregando() {
        progressBar.visibility = View.VISIBLE
        txtErro.visibility = View.GONE
    }

    private fun processarDocumento(doc: Document) {
        val header = doc.selectFirst("div.card-header div")
        val content = doc.selectFirst("div.card-body p.contato-info")

        if (content != null) {
            val titulo = header?.text()?.replace(" - ", " – ") ?: ""
            val conteudoHtml = content.html()

            cache.saveContent(currentUrl, "$titulo|||$conteudoHtml")
            exibirConteudoCache("$titulo|||$conteudoHtml")
        } else {
            verificarCacheFallback()
        }
    }

    private fun verificarCacheFallback() {
        val cachedData = cache.loadContent(currentUrl)
        if (cachedData != null) {
            exibirConteudoCache(cachedData)
        } else {
            exibirErro("Não foi possível carregar o conteúdo.")
        }
    }

    private fun exibirErro(mensagem: String) {
        txtErro.text = mensagem
        txtErro.visibility = View.VISIBLE
        txtTitulo.visibility = View.GONE
        txtConteudo.visibility = View.GONE
        barraCompartilhamento.visibility = View.GONE
    }

    private inner class CacheHelper(context: Context) {
        private val prefs: SharedPreferences = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

        fun saveContent(url: String, data: String) {
            val key = "$CACHE_KEY_PREFIX${url.hashCode()}"
            prefs.edit { putString(key, data) }
        }

        fun loadContent(url: String): String? {
            val key = "$CACHE_KEY_PREFIX${url.hashCode()}"
            return prefs.getString(key, null)
        }
    }
}