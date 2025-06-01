package com.marinov.colegioetapalegacy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class WebViewFragment extends Fragment {
    private static final String ARG_URL = "url";
    private static final String HOME_PATH = "https://areaexclusiva.colegioetapa.com.br/home";
    private static final String PREFS_NAME = "app_prefs";
    private static final String AUTOFILL_PREFS = "autofill_prefs";
    private static final String KEY_ASKED_STORAGE = "asked_storage";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1;

    private GeckoView geckoView;
    private GeckoRuntime geckoRuntime;
    private GeckoSession geckoSession;
    private boolean canGoBack = false;

    private LinearLayout layoutError;
    private LinearLayout layoutSemInternet;
    private Button btnTentarNovamente;
    private SharedPreferences sharedPrefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        sharedPrefs = requireContext()
                .getSharedPreferences(AUTOFILL_PREFS, Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_webview, container, false);

        layoutError = view.findViewById(R.id.layout_sem_internet);
        geckoView = view.findViewById(R.id.geckoview);
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);

        if (!isOnline()) {
            showNoInternetUI();
        } else {
            initializeGeckoView();
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Botão "Voltar" do Android
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (canGoBack && geckoSession != null) {
                    geckoSession.goBack();
                } else {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    /**
     * Cria canal de notificação para downloads.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Downloads");
            NotificationManager mgr =
                    requireContext().getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    /**
     * Inicializa GeckoRuntime, GeckoSession e configura os delegates
     * (Navigation, Progress, Content), sem ocultar nem animar o GeckoView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeGeckoView() {
        // 1) Cria o GeckoRuntime (API ≥ 18)
        geckoRuntime = GeckoRuntime.create(requireContext());

        // 2) Cria e abre a GeckoSession
        geckoSession = new GeckoSession();
        geckoSession.open(geckoRuntime);

        // 3) NavigationDelegate: intercepta "jsbridge://save" e rastreia canGoBack
        geckoSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            public boolean onLoadRequest(@NonNull GeckoSession session, @NonNull String uri) {
                if (uri.startsWith("jsbridge://save")) {
                    Uri parsed = Uri.parse(uri);
                    String user = parsed.getQueryParameter("user");
                    String pass = parsed.getQueryParameter("pass");
                    if (user != null && pass != null) {
                        sharedPrefs.edit()
                                .putString("user", user)
                                .putString("password", pass)
                                .apply();
                    }
                    return true;
                }
                if (uri.equals("jsbridge://closeHome")) {
                    closeHomeFragment();
                    requireActivity()
                            .getSupportFragmentManager()
                            .popBackStack();
                    return true;
                }
                return false;
            }

            @Override
            public void onLocationChange(@NonNull GeckoSession session, @Nullable String uri) { }

            public void onProgressChange(@NonNull GeckoSession session, int progress) { }

            public void onSecurityChange(@NonNull GeckoSession session, int state) { }

            @Override
            public void onCanGoBack(@NonNull GeckoSession session, boolean canGoBackFlag) {
                canGoBack = canGoBackFlag;
            }

            @Override
            public void onCanGoForward(@NonNull GeckoSession session, boolean canGoForwardFlag) { }
        });

        // 4) ProgressDelegate: injeta CSS/JS e mostra o GeckoView sem ocultação
        geckoSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            public void onLoadEnd(@NonNull GeckoSession session, @NonNull String uri) {
                restoreCookies();
                injectRemoveHeaderAndAutofill();
                // simplesmente deixa o GeckoView visível (já é "visible" no XML)
                layoutError.setVisibility(View.GONE);
                layoutSemInternet.setVisibility(View.GONE);

                if (uri.startsWith(HOME_PATH)) {
                    String jsCheck =
                            "(function(){" +
                                    " var a = document.querySelector(\"#home_banners_carousel > div > div.carousel-item.active > a > img\");" +
                                    " var b = document.querySelector(\"#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > div.card.bg-transparent;border-0 > div:nth-child(4) > div.col-12.col-lg-8.mb-5 > div > div.d-flex.flex-column.h-100.mb-2 > div.card.border-radius-card.mb-3.border-blue\");" +
                                    " if(a!==null && b!==null) window.location.href='jsbridge://closeHome';" +
                                    "})()";
                    geckoSession.loadUri("javascript:" + jsCheck);
                }
            }

            @Override
            public void onPageStop(@NonNull GeckoSession session, boolean success) { }
        });

        // 5) ContentDelegate: captura pedidos de download
        geckoSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            public void onDownloadRequested(@NonNull GeckoSession session,
                                            @NonNull String url,
                                            @Nullable String contentDisposition,
                                            @Nullable String mimeType,
                                            long contentLength) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                        && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
                String referer = "";
                String fileName = android.webkit.URLUtil.guessFileName(
                        url, contentDisposition, mimeType);
                downloadManually(url, fileName, cookies, referer, mimeType);
            }

            public void onShowBackgroundImage(@NonNull GeckoSession session, @NonNull String url) { }

            public void onMediaBlocked(@NonNull GeckoSession session, @NonNull String url) { }

            public void onContentLoaded(@NonNull GeckoSession session) { }

            public void onCollectTelemetry(@NonNull GeckoSession session, @Nullable String data) { }

            public void onContentIntent(@NonNull GeckoSession session, @NonNull String action,
                                        @Nullable Uri uri, @Nullable String mimeType,
                                        @Nullable String[] data, boolean isForeground) { }
        });

        // 6) Liga a sessão ao GeckoView
        geckoView.setSession(geckoSession);

        // 7) Carrega a URL inicial (sem ocultar nada)
        String initialUrl = getArguments() != null
                ? getArguments().getString(ARG_URL)
                : null;
        if (initialUrl != null) {
            geckoSession.loadUri(initialUrl);
        }

        // 8) Ajustes de segurança (bloquear long-click, haptic feedback)
        setupGeckoViewSecurity();

        // 9) Solicita permissão de WRITE_EXTERNAL_STORAGE se necessário
        checkStoragePermissions();
    }

    /** Equivalente a CookieManager.flush() do WebView */
    private void restoreCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().flush();
        }
    }

    private void injectRemoveHeaderAndAutofill() {
        // Script de remoção de elementos
        String jsRemoveHeader =
                "(function(){" +
                        "document.documentElement.style.webkitTouchCallout='none';" +
                        "document.documentElement.style.webkitUserSelect='none';" +
                        "var nav = document.querySelector('#page-content-wrapper > nav'); if(nav) nav.remove();" +
                        "var sidebar = document.querySelector('#sidebar-wrapper'); if(sidebar) sidebar.remove();" +
                        "var responsavelTab = document.querySelector('#responsavel-tab'); if(responsavelTab) responsavelTab.remove();" +
                        "var alunoTab = document.querySelector('#aluno-tab'); if(alunoTab) alunoTab.remove();" +
                        "var login = document.querySelector('#login'); if(login) login.remove();" +
                        "var cardElement = document.querySelector('body > div.row.mx-0.pt-4 > div > div.card.mt-4.border-radius-card.border-0.shadow'); if(cardElement) cardElement.remove();" +
                        "var style = document.createElement('style');" +
                        "style.type = 'text/css';" +
                        "style.appendChild(document.createTextNode('::-webkit-scrollbar{display:none;}'));" +
                        "document.head.appendChild(style);" +
                        "})()";

        // Obtém credenciais salvas
        String user = sharedPrefs.getString("user", "");
        String pass = sharedPrefs.getString("password", "");

        // Escapar caracteres especiais
        String escapedUser = user.replace("\\", "\\\\").replace("'", "\\'");
        String escapedPass = pass.replace("\\", "\\\\").replace("'", "\\'");

        // Script de autofill com MutationObserver
        String jsAutofill =
                "(function(){" +
                        "   const observerConfig={childList:true,subtree:true};" +
                        "   const userFields=['#matricula'];" +
                        "   const passFields=['#senha'];" +
                        "   function setupAutofill(){" +
                        "       const userField=document.querySelector(userFields.join(','));" +
                        "       const passField=document.querySelector(passFields.join(','));" +
                        "       if(userField&&passField){" +
                        "           if(userField.value==='') userField.value='" + escapedUser + "';" +
                        "           if(passField.value==='') passField.value='" + escapedPass + "';" +
                        "           function handleInput(){" +
                        "               const u=encodeURIComponent(userField.value);" +
                        "               const p=encodeURIComponent(passField.value);" +
                        "               window.location.href='jsbridge://save?user='+u+'&pass='+p;" +
                        "           }" +
                        "           userField.addEventListener('input',handleInput);" +
                        "           passField.addEventListener('input',handleInput);" +
                        "           return true;" +
                        "       }" +
                        "       return false;" +
                        "   }" +
                        "   if(!setupAutofill()){" +
                        "       const observer=new MutationObserver((mutations)=>{ if(setupAutofill()) observer.disconnect(); });" +
                        "       observer.observe(document.body,observerConfig);" +
                        "   }" +
                        "   document.querySelectorAll('.nav-link').forEach(tab=>{" +
                        "       tab.addEventListener('click',()=>{ setTimeout(setupAutofill,300); });" +
                        "   });" +
                        "})()";

        // Executa ambos os scripts
        geckoSession.loadUri("javascript:" + jsRemoveHeader + jsAutofill);
    }

    private void setupGeckoViewSecurity() {
        geckoView.setClickable(true);
        geckoView.setLongClickable(false);
        geckoView.setHapticFeedbackEnabled(false);
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showNoInternetUI() {
        layoutError.setVisibility(View.GONE);
        layoutSemInternet.setVisibility(View.VISIBLE);
        btnTentarNovamente.setOnClickListener(v -> {
            if (isOnline()) {
                layoutSemInternet.setVisibility(View.GONE);
                if (geckoSession != null && geckoSession.isOpen()) {
                    geckoSession.reload();
                }
            } else {
                Toast.makeText(requireContext(),
                        "Sem conexão com a internet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkStoragePermissions() {
        SharedPreferences prefs =
                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                && !prefs.getBoolean(KEY_ASKED_STORAGE, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply();
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    private void downloadManually(String url,
                                  String fileName,
                                  String cookies,
                                  String referer,
                                  @Nullable String mimeType) {
        NotificationCompat.Builder notif = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Download")
                .setContentText("Baixando " + fileName)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        NotificationManagerCompat.from(requireContext())
                .notify(NOTIFICATION_ID, notif.build());

        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            OutputStream out = null;
            Uri targetUri = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", System.getProperty("http.agent"));
                conn.setRequestProperty("Referer", referer);
                conn.connect();

                int code = conn.getResponseCode();
                if (code / 100 == 3) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    downloadManually(loc, fileName, cookies, referer, mimeType);
                    return;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + code);
                }

                in = conn.getInputStream();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    if (mimeType != null) values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    targetUri = requireContext().getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    out = requireContext().getContentResolver().openOutputStream(targetUri);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File outFile = new File(dir, fileName);
                    out = new FileOutputStream(outFile);
                    targetUri = Uri.fromFile(outFile);
                }

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                if (out != null) out.flush();

                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(targetUri,
                        mimeType != null ? mimeType : "*/*");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pi = PendingIntent.getActivity(
                        requireContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                notif.setContentText("Concluído: " + fileName)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi)
                        .setOngoing(false)
                        .setAutoCancel(true);
                NotificationManagerCompat.from(requireContext())
                        .notify(NOTIFICATION_ID, notif.build());

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    requireContext().sendBroadcast(
                            new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
                }
            } catch (Exception e) {
                Log.e("DownloadManual", "erro", e);
                notif.setContentText("Falha: " + fileName)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setOngoing(false)
                        .setAutoCancel(true);
                NotificationManagerCompat.from(requireContext())
                        .notify(NOTIFICATION_ID, notif.build());
            } finally {
                if (conn != null) conn.disconnect();
                try {
                    if (in != null) in.close();
                } catch (IOException ignored) {}
                try {
                    if (out != null) out.close();
                } catch (IOException ignored) {}
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (geckoSession != null) {
            geckoSession.reload();
        }
    }

    @Override
    public void onDestroyView() {
        if (geckoSession != null) {
            geckoSession.close();
            geckoSession = null;
        }
        if (geckoView != null) {
            geckoView.setSession(null);
            geckoView = null;
        }
        super.onDestroyView();
    }

    /** Cria um Bundle com a URL para instanciar este fragmento. */
    public static Bundle createArgs(String url) {
        Bundle b = new Bundle();
        b.putString(ARG_URL, url);
        return b;
    }

    /** Procura HomeFragment na stack e chama killFragment() nele. */
    private void closeHomeFragment() {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        for (Fragment fragment : fm.getFragments()) {
            if (fragment instanceof HomeFragment) {
                ((HomeFragment) fragment).killFragment();
                break;
            }
        }
    }
}