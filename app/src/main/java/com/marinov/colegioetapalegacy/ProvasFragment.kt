package com.marinov.colegioetapalegacy

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

class ProvasFragment : Fragment() {

    private companion object {
        const val TAG = "ProvasFragment"
    }

    private lateinit var searchView: SearchView
    private lateinit var recyclerProvas: RecyclerView
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var adapter: ProvasAdapter
    private val allItems = mutableListOf<RepoItem>()
    private var currentPath = ""
    private var fetchTask: FetchFilesTask? = null
    private lateinit var layoutSemInternet: LinearLayout
    private lateinit var btnTentarNovamente: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_provas, container, false)
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet)
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente)
        searchView = view.findViewById(R.id.search_view)
        recyclerProvas = view.findViewById(R.id.recyclerProvas)
        progressBar = view.findViewById(R.id.progress_circular)

        // Configuração da SearchView
        searchView.queryHint = "Buscar provas..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        recyclerProvas.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProvasAdapter(emptyList()) { item -> onItemClick(item) }
        recyclerProvas.adapter = adapter

        if (hasInternetConnection()) {
            checkAuthentication()
        } else {
            showNoInternetUI()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPath.isNotEmpty()) {
                    currentPath = getParentPath(currentPath)
                    startFetch()
                } else {
                    navigateToHomeFragment()
                    isEnabled = false
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchTask?.cancel(true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun checkAuthentication() {
        val authCheckWebView = WebView(requireContext()).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        evaluateJavascript(
                            "(function() { " +
                                    "return document.querySelector('#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                    "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > div:nth-child(2) > div.card-body > table') !== null; " +
                                    "})();"
                        ) { value ->
                            val isAuthenticated = "true" == value
                            if (isAuthenticated) {
                                startFetch()
                            } else {
                                showNoInternetUI()
                            }
                            destroy()
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    showNoInternetUI()
                    destroy()
                }
            }

            loadUrl("https://areaexclusiva.colegioetapa.com.br/provas/notas")
        }
    }

    private fun navigateToHomeFragment() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.navigation_home
    }

    private fun getParentPath(path: String): String {
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash == -1) "" else path.substring(0, lastSlash)
    }

    private fun showNoInternetUI() {
        recyclerProvas.visibility = View.GONE
        searchView.visibility = View.GONE
        layoutSemInternet.visibility = View.VISIBLE

        btnTentarNovamente.setOnClickListener {
            if (hasInternetConnection()) {
                layoutSemInternet.visibility = View.GONE
                checkAuthentication()
            }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val netInfo = cm.activeNetworkInfo
            netInfo != null && netInfo.isConnected
        }
    }

    private fun startFetch() {
        recyclerProvas.visibility = View.VISIBLE
        searchView.visibility = View.VISIBLE
        fetchTask?.cancel(true)
        fetchTask = FetchFilesTask(this).apply {
            execute(currentPath)
        }
    }

    private fun onItemClick(item: RepoItem) {
        if (item.type == "dir") {
            currentPath = item.path
            startFetch()
        } else {
            val request = DownloadManager.Request(Uri.parse(item.downloadUrl)).apply {
                setMimeType("*/*")
                addRequestHeader("User-Agent", "EtapaApp")
                setTitle(item.name)
                setDescription("Baixando arquivo...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.name)
            }

            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
            dm?.enqueue(request)
        }
    }

    private fun filterList(query: String?) {
        val lower = query?.lowercase() ?: ""
        val filtered = allItems.filter { it.name.lowercase().contains(lower) }
        adapter.updateData(filtered)
    }

    fun onFetchCompleted(results: List<RepoItem>) {
        progressBar.visibility = View.GONE
        allItems.clear()
        allItems.addAll(results)
        adapter.updateData(results)
    }

    data class RepoItem(
        val name: String,
        val type: String,
        val path: String,
        val downloadUrl: String
    )

    private class ProvasAdapter(
        private var items: List<RepoItem>,
        private val listener: (RepoItem) -> Unit
    ) : RecyclerView.Adapter<ProvasAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_prova, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.text.text = item.name
            holder.icon.setImageResource(
                if (item.type == "dir") R.drawable.ic_folder else R.drawable.ic_file
            )
            holder.itemView.setOnClickListener { listener(item) }
        }

        override fun getItemCount(): Int = items.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newItems: List<RepoItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.item_icon)
            val text: TextView = itemView.findViewById(R.id.item_text)
        }
    }

    private class FetchFilesTask(frag: ProvasFragment) : AsyncTask<String, Void, List<RepoItem>>() {
        private val fragRef = WeakReference(frag)
        private val githubToken: String = BuildConfig.GITHUB_PAT

        override fun onPreExecute() {
            fragRef.get()?.progressBar?.visibility = View.VISIBLE
        }

        override fun doInBackground(vararg params: String?): List<RepoItem> {
            val path = params[0] ?: ""
            val list = mutableListOf<RepoItem>()
            var conn: HttpURLConnection? = null
            try {
                val api = "https://api.github.com/repos/gmb7886/schooltests/contents" +
                        if (path.isEmpty()) "" else "/$path"
                val url = URL(api)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "EtapaApp")
                conn.setRequestProperty("Authorization", "token $githubToken")

                if (conn.responseCode != HttpURLConnection.HTTP_OK) return list

                val inputStream: InputStream = conn.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val arr = JSONArray(sb.toString())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        RepoItem(
                            o.getString("name"),
                            o.getString("type"),
                            o.getString("path"),
                            o.optString("download_url", "")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar arquivos", e)
            } finally {
                conn?.disconnect()
            }
            return list
        }

        override fun onPostExecute(result: List<RepoItem>) {
            fragRef.get()?.onFetchCompleted(result)
        }
    }
}