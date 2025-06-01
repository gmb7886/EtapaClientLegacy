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
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class CardapioFragment extends Fragment {
    public static final String URL = "https://areaexclusiva.colegioetapa.com.br/cardapio";

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ASKED_STORAGE = "asked_storage";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1;

    private WebView webView;
    private LinearLayout layoutSemInternet;
    private Button btnTentarNovamente;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
    }

    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);
        webView = view.findViewById(R.id.webview);

        if (isOnline()) {
            initializeWebView();
        } else {
            showNoInternetUI();
        }

        return view;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Downloads");
            NotificationManager manager = requireContext().getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void navigateToHome() {
        try {
            BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
            bottomNav.setSelectedItemId(R.id.navigation_home);
        } catch (Exception ignored) {}
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    private void initializeWebView() {
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVisibility(View.INVISIBLE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        setupWebViewSecurity();
        restoreCookies();
        checkStoragePermissions();
        webView.loadUrl(URL);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                saveCookies();
                removeHeader(view);
                showWebViewWithAnimation(view);
                layoutSemInternet.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (!isOnline()) showNoInternetUI();
            }
        });

        configureDownloadListener();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configurar o callback do botão voltar
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    navigateToHome();
                }
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                onBackPressedCallback
        );
    }

    private void setupWebViewSecurity() {
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
    }

    private void removeHeader(WebView view) {
        String js = "document.documentElement.style.webkitTouchCallout='none';" +
                "document.documentElement.style.webkitUserSelect='none';" +
                "var nav=document.querySelector('#page-content-wrapper > nav'); if(nav) nav.remove();" +
                "var sidebar=document.querySelector('#sidebar-wrapper'); if(sidebar) sidebar.remove();" +
                "var style=document.createElement('style');" +
                "style.type='text/css';" +
                "style.appendChild(document.createTextNode('::-webkit-scrollbar{display:none;}'));" +
                "document.head.appendChild(style);";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(js, null);
        }
    }

    private void showWebViewWithAnimation(WebView view) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(300).start();
        }, 100);
    }

    private void showNoInternetUI() {
        webView.setVisibility(View.GONE);
        layoutSemInternet.setVisibility(View.VISIBLE);
        btnTentarNovamente.setOnClickListener(v -> navigateToHome());
        if (isOnline()) {
            layoutSemInternet.setVisibility(View.GONE);
            webView.reload();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void restoreCookies() {}
    private void saveCookies()   {}

    private void checkStoragePermissions() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                !prefs.getBoolean(KEY_ASKED_STORAGE, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply();
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION
                );
            }
        }
    }

    private void configureDownloadListener() {
        webView.setDownloadListener((url, ua, cd, mt, cl) -> {
            if (needsStoragePermission() && !hasStoragePermission()) return;
            String cookies = CookieManager.getInstance().getCookie(url);
            String userAgent = webView.getSettings().getUserAgentString();
            String referer = webView.getUrl();
            String fileName = URLUtil.guessFileName(url, cd, mt);
            downloadManualmente(url, fileName, cookies, userAgent, referer, mt);
        });
    }

    private void downloadManualmente(
            String url,
            String fileName,
            String cookies,
            String userAgent,
            String referer,
            @Nullable String mimeType
    ) {
        NotificationCompat.Builder notif = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Download")
                .setContentText("Baixando " + fileName)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        NotificationManagerCompat.from(requireContext()).notify(NOTIFICATION_ID, notif.build());

        Executors.newSingleThreadExecutor().execute(() -> {
            InputStream in = null; OutputStream out = null; HttpURLConnection conn = null; Uri targetUri = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Referer", referer);
                conn.connect();

                int code = conn.getResponseCode();
                if (code / 100 == 3) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    downloadManualmente(loc, fileName, cookies, userAgent, referer, mimeType);
                    return;
                }
                if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);

                in = conn.getInputStream();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    if (mimeType != null) values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    targetUri = requireContext().getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (targetUri == null) throw new IOException("MediaStore falhou");
                    out = requireContext().getContentResolver().openOutputStream(targetUri);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File outFile = new File(dir, fileName);
                    out = new FileOutputStream(outFile);
                    targetUri = Uri.fromFile(outFile);
                }
                byte[] buf = new byte[8192]; int len;
                while ((len = in.read(buf)) != -1) {
                    if (out != null) out.write(buf, 0, len);
                }
                if (out != null) out.flush();

                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(targetUri, mimeType != null ? mimeType : "*/*");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pi = PendingIntent.getActivity(
                        requireContext(), 0, openIntent, flags
                );

                notif.setContentText("Concluído: " + fileName)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi);
                NotificationManagerCompat.from(requireContext()).notify(NOTIFICATION_ID, notif.build());

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    requireContext().sendBroadcast(
                            new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri)
                    );
                }
            } catch (Exception e) {
                Log.e("DownloadManual","erro",e);
                notif.setContentText("Falha: " + fileName)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setSmallIcon(android.R.drawable.stat_notify_error);
                NotificationManagerCompat.from(requireContext()).notify(NOTIFICATION_ID, notif.build());
            } finally {
                if (conn != null) conn.disconnect();
                try { if (in != null) in.close(); } catch (IOException ignored) {}
                try { if (out != null) out.close(); } catch (IOException ignored) {}
            }
        });
    }

    private boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (webView != null) {
            webView.reload();
        }
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            ((ViewGroup)webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}