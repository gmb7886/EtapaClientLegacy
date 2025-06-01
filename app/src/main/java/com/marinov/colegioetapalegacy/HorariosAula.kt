package com.marinov.colegioetapalegacy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import androidx.core.content.edit

class HorariosAula : Fragment() {

    private companion object {
        const val URL_HORARIOS = "https://areaexclusiva.colegioetapa.com.br/horarios/aulas"
        const val PREFS = "horarios_prefs"
        const val KEY_HTML = "cache_html_horarios"
    }

    private lateinit var tableHorarios: TableLayout
    private lateinit var barOffline: LinearLayout
    private lateinit var cache: CacheHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_horarios, container, false)
        tableHorarios = root.findViewById(R.id.tableHorarios)
        barOffline = root.findViewById(R.id.barOffline)
        val btnLogin: Button = root.findViewById(R.id.btnLogin)
        cache = CacheHelper(requireContext())

        btnLogin.setOnClickListener {
            try {
                activity?.findViewById<LinearLayout>(R.id.bottom_navigation)
                    ?.findViewById<View>(R.id.navigation_home)
                    ?.performClick()
            } catch (e: Exception) {
                Log.e("HorariosAula", "Erro ao navegar para home", e)
            }
        }

        fetchHorarios()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    activity?.findViewById<LinearLayout>(R.id.bottom_navigation)
                        ?.findViewById<View>(R.id.navigation_home)
                        ?.performClick()
                } catch (e: Exception) {
                    Log.e("HorariosAula", "Erro ao navegar para home", e)
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun fetchHorarios() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    try {
                        val cookieHeader = CookieManager.getInstance().getCookie(URL_HORARIOS) ?: ""
                        Jsoup.connect(URL_HORARIOS)
                            .header("Cookie", cookieHeader)
                            .userAgent("Mozilla/5.0")
                            .timeout(15000)
                            .get()
                    } catch (e: Exception) {
                        Log.e("HorariosAula", "Erro ao conectar", e)
                        null
                    }
                }

                if (doc != null) {
                    val table = doc.selectFirst(
                        "#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > " +
                                "div > div.card-body > table"
                    )

                    if (table != null) {
                        cache.saveHtml(table.outerHtml())
                        parseAndBuildTable(table)
                        hideOfflineBar()
                    } else {
                        showOfflineBar()
                        Log.e("HorariosAula", "Tabela não encontrada no HTML")
                        loadCachedData()
                    }
                } else {
                    loadCachedData()
                    showOfflineBar()
                    Log.e("HorariosAula", "Falha na conexão — usando cache")
                }
            } catch (e: Exception) {
                Log.e("HorariosAula", "Erro inesperado", e)
                loadCachedData()
                showOfflineBar()
            }
        }
    }

    private fun loadCachedData() {
        val html = cache.loadHtml()
        if (html != null) {
            val fake = Jsoup.parse(html)
            val table = fake.selectFirst("table")
            if (table != null) {
                parseAndBuildTable(table)
            }
        }
    }

    private fun parseAndBuildTable(table: Element) {
        tableHorarios.removeAllViews()
        val headerBgColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val textColor = ContextCompat.getColor(requireContext(), android.R.color.black)

        // Cabeçalho
        val headerRowHtml = table.selectFirst("thead > tr")
        if (headerRowHtml != null) {
            val headerRow = TableRow(requireContext())
            headerRow.setBackgroundColor(headerBgColor)
            for (th in headerRowHtml.select("th")) {
                val tv = createCell(th.text(), true)
                tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                headerRow.addView(tv)
            }
            tableHorarios.addView(headerRow)
        }

        // Linhas de dados
        val rows = table.select("tbody > tr")
        for (tr in rows) {
            // Ignorar linhas com alerta de intervalo
            if (tr.select("div.alert-info").isNotEmpty()) continue

            val row = TableRow(requireContext())
            row.setBackgroundResource(android.R.color.transparent)

            for (cell in tr.children()) {
                val isHeaderCell = cell.tagName() == "th"
                val tv = createCell(cell.text(), isHeaderCell)
                tv.setTextColor(textColor)

                // Verificando classes de cor (ajustar conforme necessário)
                if (cell.hasClass("bg-primary")) {
                    tv.setBackgroundResource(R.drawable.bg_primary_rounded)
                    tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                }
                row.addView(tv)
            }
            tableHorarios.addView(row)
        }
    }

    private fun createCell(text: String, isHeader: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isHeader) 14f else 13f)
            typeface = Typeface.defaultFromStyle(if (isHeader) Typeface.BOLD else Typeface.NORMAL)

            val padH = (12 * resources.displayMetrics.density).toInt()
            val padV = (8 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)

            val minWidth = (80 * resources.displayMetrics.density).toInt()
            setMinWidth(minWidth)

            layoutParams = TableRow.LayoutParams(
                0,
                TableRow.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(2, 2, 2, 2)
            }

            gravity = android.view.Gravity.CENTER
        }
    }

    private fun showOfflineBar() {
        barOffline.visibility = View.VISIBLE
    }

    private fun hideOfflineBar() {
        barOffline.visibility = View.GONE
    }

    private inner class CacheHelper(context: Context) {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun saveHtml(html: String) {
            prefs.edit { putString(KEY_HTML, html) }
        }

        fun loadHtml(): String? {
            return prefs.getString(KEY_HTML, null)
        }
    }
}