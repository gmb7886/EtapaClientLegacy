package com.marinov.colegioetapalegacy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class EADFragment extends Fragment {
    private static final String AUTH_CHECK_URL = "https://areaexclusiva.colegioetapa.com.br/provas/notas";
    private static final String EAD_URL = BuildConfig.EAD_URL;
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ASKED_STORAGE = "asked_storage";
    private static final String KEY_SHOW_WARNING = "show_warning";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private WebView webView;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customView;
    private int originalOrientation;
    private LinearLayout layoutSemInternet;
    private MaterialButton btnTentarNovamente;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);
        fullscreenContainer = view.findViewById(R.id.fullscreen_container);
        webView = view.findViewById(R.id.webview);

        if (hasInternetConnection()) {
            checkAuthentication();
        } else {
            showNoInternetUI();
        }

        return view;
    }

    private void checkAndShowWarningDialog() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shouldShowWarning = prefs.getBoolean(KEY_SHOW_WARNING, true);

        if (shouldShowWarning) {
            showWarningDialog(prefs);
        }
    }

    private void showWarningDialog(SharedPreferences prefs) {
        new AlertDialog.Builder(requireContext())
                .setTitle("ATENÇÃO!")
                .setMessage("Essa função é instável e pode não funcionar em todos os dispositivos! Essa página só exibe uma página com o compartilhamento de arquivos dos EADs antigos, caso tenha problemas em acessar mande um email para: gmb7886@outlook.com.br. Recomendado uso em tablets para melhor visibilidade.")
                .setPositiveButton("Entendi", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Não mostrar novamente", (dialog, which) -> {
                    prefs.edit().putBoolean(KEY_SHOW_WARNING, false).apply();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configurar o callback do botão voltar
        // Retrocede no WebView
        // Navega para o HomeFragment via BottomNavigation
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack(); // Retrocede no WebView
                } else {
                    // Navega para o HomeFragment via BottomNavigation
                    BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
                    bottomNav.setSelectedItemId(R.id.navigation_home);
                }
            }
        };

        // Registrar o callback no dispatcher
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                onBackPressedCallback
        );
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void checkAuthentication() {
        WebView authCheckWebView = new WebView(requireContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(authCheckWebView, true);
        }
        WebSettings authSettings = authCheckWebView.getSettings();
        authSettings.setJavaScriptEnabled(true);
        authSettings.setDomStorageEnabled(true);

        authCheckWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    view.evaluateJavascript(
                            "(function() { " +
                                    "return document.querySelector('#page-content-wrapper > div.d-lg-flex > div.container-fluid.p-3 > " +
                                    "div.card.bg-transparent.border-0 > div.card-body.px-0.px-md-3 > div:nth-child(2) > div.card-body > table') !== null; " +
                                    "})();",
                            value -> {
                                boolean isAuthenticated = value.equals("true");
                                if (isAuthenticated) {
                                    checkAndShowWarningDialog();
                                    initializeWebView();
                                } else {
                                    showNoInternetUI();
                                }
                                authCheckWebView.destroy();
                            }
                    );
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                showNoInternetUI();
                authCheckWebView.destroy();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                showNoInternetUI();
                authCheckWebView.destroy();
            }
        });
        authCheckWebView.loadUrl(AUTH_CHECK_URL);
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongConstant"})
    private void initializeWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }

        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        checkStoragePermissions();
        webView.loadUrl(EAD_URL);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                Activity activity = getActivity();
                if (activity != null) {
                    originalOrientation = activity.getRequestedOrientation();
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }

                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view);
                customView = view;
                customViewCallback = callback;
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;

                Activity activity = getActivity();
                if (activity != null) {
                    activity.setRequestedOrientation(originalOrientation);
                }

                fullscreenContainer.setVisibility(View.GONE);
                fullscreenContainer.removeView(customView);
                customViewCallback.onCustomViewHidden();
                customView = null;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            private boolean pageLoaded = false;

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoaded = false; // Reset ao iniciar novo carregamento
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                showWebViewWithAnimation(view);
                layoutSemInternet.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (!pageLoaded && request.isForMainFrame()) {
                        showNoInternetUI();
                    }
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (!pageLoaded && request.isForMainFrame()) {
                        showNoInternetUI();
                    }
                }
            }
        });

        configureDownloadListener();
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

        btnTentarNovamente.setOnClickListener(v -> {
            if (hasInternetConnection()) {
                layoutSemInternet.setVisibility(View.GONE);
                checkAuthentication();
            } else {
            }
        });
    }

    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
    }

    private void checkStoragePermissions() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !prefs.getBoolean(KEY_ASKED_STORAGE, false)) {
            prefs.edit().putBoolean(KEY_ASKED_STORAGE, true).apply();
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    private void configureDownloadListener() {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (needsStoragePermission() && !hasStoragePermission()) {
                return;
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
            DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        });
    }

    private boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
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