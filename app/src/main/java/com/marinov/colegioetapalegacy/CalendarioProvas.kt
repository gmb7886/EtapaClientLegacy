package com.marinov.colegioetapalegacy

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import androidx.core.content.edit

class CalendarioProvas : Fragment() {

    private companion object {
        const val URL_BASE = "https://areaexclusiva.colegioetapa.com.br/provas/datas"
        const val PREFS = "calendario_prefs"
        const val KEY_BASE = "cache_html_calendario_"
        const val KEY_SEM_PROVAS = "sem_provas_"
    }

    private lateinit var recyclerProvas: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var barOffline: View
    private lateinit var txtSemProvas: TextView
    private lateinit var txtSemDados: TextView
    private lateinit var btnLogin: AppCompatButton
    private lateinit var spinnerMes: Spinner
    private lateinit var adapter: ProvasAdapter
    private lateinit var cache: CacheHelper

    private var mesSelecionado: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_provas_calendar, container, false)

        recyclerProvas = root.findViewById(R.id.recyclerProvas)
        progressBar = root.findViewById(R.id.progress_circular)
        barOffline = root.findViewById(R.id.barOffline)
        txtSemProvas = root.findViewById(R.id.txt_sem_provas)
        txtSemDados = root.findViewById(R.id.txt_sem_dados)
        spinnerMes = root.findViewById(R.id.spinner_mes)
        btnLogin = root.findViewById(R.id.btnLogin)

        setupRecyclerView()
        configurarSpinnerMeses()
        cache = CacheHelper(requireContext())
        carregarDadosParaMes()

        btnLogin.setOnClickListener {
            val navView = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            navView?.selectedItemId = R.id.navigation_home
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
                bottomNav.selectedItemId = R.id.navigation_home
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupRecyclerView() {
        recyclerProvas.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProvasAdapter(emptyList(), this)
        recyclerProvas.adapter = adapter
    }

    private fun configurarSpinnerMeses() {
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.meses_array,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinnerMes.adapter = adapter
        spinnerMes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mesSelecionado = position
                carregarDadosParaMes()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun carregarDadosParaMes() {
        if (!isOnline()) {
            exibirBarraOffline()
            verificarCache()
            return
        }

        val url = if (mesSelecionado == 0) URL_BASE else "$URL_BASE?mes%5B%5D=$mesSelecionado"
        fetchProvas(url)
    }

    private fun verificarCache() {
        when {
            cache.temProvas(mesSelecionado) -> carregarCacheProvas()
            cache.mesSemProvas(mesSelecionado) -> exibirMensagemSemProvas()
            else -> exibirSemDados()
        }
    }

    private fun exibirMensagemSemProvas() {
        recyclerProvas.visibility = View.GONE
        txtSemProvas.visibility = View.VISIBLE
        barOffline.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun carregarCacheProvas() {
        val html = cache.loadHtml(mesSelecionado)
        if (html != null) {
            val fake = Jsoup.parse(html)
            val table = fake.selectFirst("table")
            if (table != null) {
                parseAndDisplayTable(table)
                recyclerProvas.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchProvas(url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                exibirCarregando()

                val doc = withContext(Dispatchers.IO) {
                    try {
                        val cookieHeader = CookieManager.getInstance().getCookie(url)
                        Jsoup.connect(url)
                            .header("Cookie", cookieHeader)
                            .userAgent("Mozilla/5.0")
                            .timeout(15000)
                            .get()
                    } catch (e: Exception) {
                        Log.e("CalendarioProvas", "Erro na conexão", e)
                        null
                    }
                }

                progressBar.visibility = View.GONE

                if (doc != null) {
                    val table = doc.selectFirst(
                        "#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > " +
                                "div.card.mb-5.bg-transparent.text-white.border-0 > table"
                    )
                    val alerta = doc.selectFirst(
                        "#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > " +
                                "div.alert.alert-info.text-center"
                    )

                    when {
                        table != null -> {
                            cache.salvarProvas(table.outerHtml(), mesSelecionado)
                            parseAndDisplayTable(table)
                            exibirConteudoOnline()
                        }
                        alerta != null -> {
                            cache.salvarMesSemProvas(mesSelecionado)
                            exibirMensagemSemProvas()
                        }
                        else -> {
                            verificarCache()
                            exibirBarraOffline()
                        }
                    }
                } else {
                    verificarCache()
                    exibirBarraOffline()
                }
            } catch (_: Exception) {
                progressBar.visibility = View.GONE
                verificarCache()
                exibirBarraOffline()
            }
        }
    }

    private fun exibirCarregando() {
        progressBar.visibility = View.VISIBLE
        recyclerProvas.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        barOffline.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun exibirConteudoOnline() {
        recyclerProvas.visibility = View.VISIBLE
        barOffline.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        txtSemDados.visibility = View.GONE
    }

    private fun exibirBarraOffline() {
        barOffline.visibility = View.VISIBLE
    }

    private fun exibirSemDados() {
        recyclerProvas.visibility = View.GONE
        txtSemProvas.visibility = View.GONE
        barOffline.visibility = View.GONE
        txtSemDados.visibility = View.VISIBLE
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo: NetworkInfo? = cm.activeNetworkInfo
            netInfo != null && netInfo.isConnected
        } catch (_: Exception) {
            false
        }
    }

    private fun parseAndDisplayTable(table: Element) {
        val items = mutableListOf<ProvaItem>()
        val rows = table.select("tbody > tr")

        for (tr in rows) {
            val cells = tr.children()
            if (cells.size < 5) continue

            val data = cells[0].text()
            val codigo = cells[1].ownText()
            val linkElement = cells[1].selectFirst("a")
            val link = linkElement?.attr("href") ?: ""
            val tipo = cells[2].text()
            val conjunto = "${cells[3].text()}° conjunto"
            val materia = cells[4].text()

            if (data.isNotEmpty() && codigo.isNotEmpty()) {
                items.add(ProvaItem(data, codigo, link, tipo, conjunto, materia))
            }
        }

        adapter.updateData(items)
    }

    private inner class ProvasAdapter(
        private var items: List<ProvaItem>,
        private val parentFragment: Fragment
    ) : RecyclerView.Adapter<ProvasAdapter.ViewHolder>() {

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newItems: List<ProvaItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_prova_calendar, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.txtData.text = item.data
            holder.txtCodigo.text = item.codigo
            holder.txtConjunto.text = item.conjunto
            holder.txtMateria.text = item.materia

            holder.btnTipo.text = item.tipo
            holder.btnTipo.setBackgroundResource(
                if (item.tipo.contains("rec", ignoreCase = true))
                    R.drawable.bg_warning_rounded
                else
                    R.drawable.bg_primary_rounded
            )

            holder.card.setOnClickListener {
                if (parentFragment.isAdded) {
                    val transaction = parentFragment.parentFragmentManager.beginTransaction()
                    transaction.replace(R.id.nav_host_fragment, MateriadeProva.newInstance(item.link))
                    transaction.addToBackStack(null)
                    transaction.commit()
                }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: View = itemView.findViewById(R.id.card_prova)
            val txtData: TextView = itemView.findViewById(R.id.txt_data)
            val txtCodigo: TextView = itemView.findViewById(R.id.txt_codigo)
            val txtConjunto: TextView = itemView.findViewById(R.id.txt_conjunto)
            val txtMateria: TextView = itemView.findViewById(R.id.txt_materia)
            val btnTipo: AppCompatButton = itemView.findViewById(R.id.btn_tipo) // Corrigido para AppCompatButton
        }
    }

    private data class ProvaItem(
        val data: String,
        val codigo: String,
        val link: String,
        val tipo: String,
        val conjunto: String,
        val materia: String
    )

    private inner class CacheHelper(context: Context) {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun salvarProvas(html: String, mes: Int) {
            prefs.edit {
                putString("$KEY_BASE$mes", html)
                    .remove("$KEY_SEM_PROVAS$mes")
            }
        }

        fun salvarMesSemProvas(mes: Int) {
            prefs.edit {
                putBoolean("$KEY_SEM_PROVAS$mes", true)
                    .remove("$KEY_BASE$mes")
            }
        }

        fun loadHtml(mes: Int): String? {
            return prefs.getString("$KEY_BASE$mes", null)
        }

        fun temProvas(mes: Int): Boolean {
            return prefs.contains("$KEY_BASE$mes")
        }

        fun mesSemProvas(mes: Int): Boolean {
            return prefs.getBoolean("$KEY_SEM_PROVAS$mes", false)
        }
    }
}