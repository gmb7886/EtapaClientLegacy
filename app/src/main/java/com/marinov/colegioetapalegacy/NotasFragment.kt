package com.marinov.colegioetapalegacy

import android.annotation.SuppressLint
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
import java.text.DecimalFormat
import androidx.core.content.edit

class NotasFragment : Fragment() {

    private companion object {
        const val URL_NOTAS = "https://areaexclusiva.colegioetapa.com.br/provas/notas"
        const val PREFS = "notas_prefs"
        const val KEY_HTML = "cache_html"
    }

    private lateinit var tableNotas: TableLayout
    private lateinit var barOffline: LinearLayout
    private lateinit var cache: CacheHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_notas, container, false)
        tableNotas = root.findViewById(R.id.tableNotas)
        barOffline = root.findViewById(R.id.barOffline)
        val btnLogin: Button = root.findViewById(R.id.btnLogin)
        btnLogin.setOnClickListener { navigateToHome() }

        cache = CacheHelper(requireContext())
        fetchNotas()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    val bottomNav = activity?.findViewById<LinearLayout>(R.id.bottom_navigation)
                    bottomNav?.findViewById<View>(R.id.navigation_home)?.performClick()
                } catch (e: Exception) {
                    Log.e("NotasFragment", "Erro ao navegar para home", e)
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    @SuppressLint("SetTextI18n")
    private fun parseAndBuildTable(table: Element) {
        tableNotas.removeAllViews()
        val colorDefault = ContextCompat.getColor(requireContext(), android.R.color.black)
        val colorHeaderBg = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        val colorHeaderText = ContextCompat.getColor(requireContext(), android.R.color.white)

        val headers = table.select("thead th")
        if (headers.isNotEmpty()) {
            val headerRow = TableRow(requireContext())
            headerRow.setBackgroundColor(colorHeaderBg)

            val thCode = createCell(headers[1].text(), true)
            thCode.setTextColor(colorHeaderText)
            headerRow.addView(thCode)

            for (i in 2 until headers.size) {
                val th = createCell(headers[i].text(), true)
                th.setTextColor(colorHeaderText)
                headerRow.addView(th)
            }
            tableNotas.addView(headerRow)
        }

        val rows = table.select("tbody > tr")
        val notaCols = if (headers.size > 2) headers.size - 2 else 0
        val sums = DoubleArray(notaCols)
        val counts = IntArray(notaCols)

        for (r in rows) {
            val row = TableRow(requireContext())
            row.setBackgroundResource(android.R.color.transparent)
            val cols = r.children()

            val code = if (cols.size > 1) cols[1].text() else ""
            val tvCode = createCell(code, false)
            tvCode.setTextColor(colorDefault)
            row.addView(tvCode)

            for (j in 2 until cols.size) {
                val colIndex = j - 2
                val valText = cols[j].text()

                if (valText != "--") {
                    try {
                        val value = valText.toDouble()
                        sums[colIndex] += value
                        counts[colIndex]++
                    } catch (_: NumberFormatException) {
                        // Ignore non-numeric values
                    }
                }

                val tv = createCell(valText, false)
                tv.setTextColor(colorDefault)

                when {
                    cols[j].hasClass("bg-success") -> {
                        tv.setBackgroundResource(R.drawable.bg_success_rounded)
                        tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                    }
                    cols[j].hasClass("bg-warning") -> {
                        tv.setBackgroundResource(R.drawable.bg_warning_rounded)
                        tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    }
                    cols[j].hasClass("bg-danger") -> {
                        tv.setBackgroundResource(R.drawable.bg_danger_rounded)
                        tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                    }
                }
                row.addView(tv)
            }
            tableNotas.addView(row)
        }

        if (headers.isNotEmpty()) {
            val avgRow = TableRow(requireContext())
            avgRow.setBackgroundColor(colorHeaderBg)

            val firstCell = createCell("Média", true)
            firstCell.setTextColor(colorHeaderText)
            avgRow.addView(firstCell)

            val decimalFormat = DecimalFormat("#.##")
            for (k in 0 until notaCols) {
                val avgValue = if (counts[k] > 0) decimalFormat.format(sums[k] / counts[k]) else "--"
                val avgCell = createCell(avgValue, true)
                avgCell.setTextColor(colorHeaderText)
                avgRow.addView(avgCell)
            }
            tableNotas.addView(avgRow)
        }
    }

    private fun createCell(txt: String, isHeader: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = txt
            setTypeface(null, if (isHeader) Typeface.BOLD else Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isHeader) 13f else 12f)
            val h = (8 * resources.displayMetrics.density).toInt()
            val v = (6 * resources.displayMetrics.density).toInt()
            setPadding(h, v, h, v)
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun showOfflineBar() {
        barOffline.visibility = View.VISIBLE
    }

    private fun hideOfflineBar() {
        barOffline.visibility = View.GONE
    }

    private fun navigateToHome() {
        try {
            activity?.findViewById<LinearLayout>(R.id.bottom_navigation)
                ?.findViewById<View>(R.id.navigation_home)
                ?.performClick()
        } catch (e: Exception) {
            Log.e("NotasFragment", "Erro ao simular retorno à home", e)
        }
    }

    private fun fetchNotas() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    try {
                        val cookieHeader = CookieManager.getInstance().getCookie(URL_NOTAS)
                        Jsoup.connect(URL_NOTAS)
                            .header("Cookie", cookieHeader ?: "")
                            .userAgent("Mozilla/5.0")
                            .timeout(15000)
                            .get()
                    } catch (e: Exception) {
                        Log.e("NotasFragment", "Erro ao conectar", e)
                        null
                    }
                }

                if (doc != null) {
                    val newTable = doc.selectFirst(
                        "#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > " +
                                "div:nth-child(2) > div.card-body > table"
                    )
                    if (newTable != null) {
                        cache.saveHtml(newTable.outerHtml())
                        parseAndBuildTable(newTable)
                        hideOfflineBar()
                    } else {
                        showOfflineBar()
                        Log.e("NotasFragment", "Tabela não encontrada no HTML")
                        loadCachedData()
                    }
                } else {
                    loadCachedData()
                    showOfflineBar()
                    Log.e("NotasFragment", "Falha na conexão — usando cache")
                }
            } catch (e: Exception) {
                Log.e("NotasFragment", "Erro inesperado", e)
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