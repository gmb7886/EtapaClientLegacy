package com.marinov.colegioetapalegacy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager; // Changed to ViewPager v1
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String PREFS_NAME = "HomeFragmentCache";
    private static final String KEY_CAROUSEL_ITEMS = "carousel_items";
    private static final String KEY_NEWS_ITEMS = "news_items";

    private ViewPager viewPager; // Changed to ViewPager v1
    private RecyclerView newsRecyclerView;
    private LinearLayout layoutSemInternet;
    private Button btnTentarNovamente;
    private View loadingContainer;
    private View contentContainer;

    private final List<CarouselItem> carouselItems = new ArrayList<>();
    private final List<NewsItem> newsItems = new ArrayList<>();
    private static final String HOME_URL = "https://areaexclusiva.colegioetapa.com.br/home";
    private static final String OUT_URL = "https://areaexclusiva.colegioetapa.com.br";
    private TextView txtStuckHint;

    private boolean isFragmentDestroyed = false;
    private boolean shouldReloadOnResume = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment_new, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupRecyclerView();
        setupListeners();
        checkInternetAndLoadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (shouldReloadOnResume) {
            checkInternetAndLoadData();
            shouldReloadOnResume = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void initializeViews(View view) {
        loadingContainer = view.findViewById(R.id.loadingContainer);
        contentContainer = view.findViewById(R.id.contentContainer);
        layoutSemInternet = view.findViewById(R.id.layout_sem_internet);
        btnTentarNovamente = view.findViewById(R.id.btn_tentar_novamente);
        viewPager = view.findViewById(R.id.viewPager); // Now matches layout type
        newsRecyclerView = view.findViewById(R.id.newsRecyclerView);
        txtStuckHint = view.findViewById(R.id.txtStuckHint);
    }

    private void setupRecyclerView() {
        newsRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
    }

    private void setupListeners() {
        btnTentarNovamente.setOnClickListener(v -> checkInternetAndLoadData());
    }

    private void checkInternetAndLoadData() {
        if (hasInternetConnection()) {
            if (loadCache()) {
                showContentState();
                setupCarousel();
                setupNews();
                fetchDataInBackground();
            } else {
                showLoadingState();
                fetchDataInBackground();
            }
        } else {
            showOfflineState();
        }
    }

    private void fetchDataInBackground() {
        executor.execute(() -> {
            try {
                Document doc = fetchHomePageData();
                if (isValidSession(doc)) {
                    processPageContent(doc);
                    saveCache();
                    handler.postDelayed(this::updateUIWithNewData, 5000);
                } else {
                    clearCache();
                    handler.post(this::handleInvalidSession);
                }
            } catch (IOException e) {
                handler.post(() -> handleDataFetchError(e));
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateUIWithNewData() {
        if (isFragmentDestroyed) return;

        if (viewPager.getAdapter() != null) {
            viewPager.getAdapter().notifyDataSetChanged();
        }
        if (newsRecyclerView.getAdapter() != null) {
            newsRecyclerView.getAdapter().notifyDataSetChanged();
        }

        if (loadingContainer.getVisibility() == View.VISIBLE) {
            showContentState();
            setupCarousel();
            setupNews();
        }
    }

    private Document fetchHomePageData() throws IOException {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(HOME_URL);

        return Jsoup.connect(HOME_URL)
                .userAgent("Mozilla/5.0")
                .header("Cookie", cookies != null ? cookies : "")
                .timeout(10000)
                .get();
    }

    private boolean isValidSession(Document doc) {
        return doc.getElementById("home_banners_carousel") != null &&
                doc.selectFirst("div.col-12.col-lg-8.mb-5") != null;
    }

    private void processPageContent(Document doc) {
        List<CarouselItem> newCarousel = new ArrayList<>();
        List<NewsItem> newNews = new ArrayList<>();

        processCarousel(doc, newCarousel);
        processNews(doc, newNews);

        carouselItems.clear();
        carouselItems.addAll(newCarousel);

        newsItems.clear();
        newsItems.addAll(newNews);
    }

    private void handleInvalidSession() {
        if (isFragmentDestroyed) return;
        navigateToWebView(OUT_URL);
        shouldReloadOnResume = true;
    }

    private void handleDataFetchError(IOException e) {
        if (isFragmentDestroyed) return;
        Log.e("HomeFragment", "Erro ao buscar dados: " + e.getMessage());

        if (loadingContainer.getVisibility() == View.VISIBLE &&
                carouselItems.isEmpty() && newsItems.isEmpty()) {
            navigateToWebView(OUT_URL);
            shouldReloadOnResume = true;
        }
    }

    private void saveCache() {
        if (isFragmentDestroyed) return;
        Context context = getContext();
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();

        String carouselJson = gson.toJson(carouselItems);
        String newsJson = gson.toJson(newsItems);

        editor.putString(KEY_CAROUSEL_ITEMS, carouselJson);
        editor.putString(KEY_NEWS_ITEMS, newsJson);
        editor.apply();
    }

    private boolean loadCache() {
        if (isFragmentDestroyed) return false;
        Context context = getContext();
        if (context == null) return false;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        String carouselJson = prefs.getString(KEY_CAROUSEL_ITEMS, null);
        String newsJson = prefs.getString(KEY_NEWS_ITEMS, null);

        if (carouselJson != null && newsJson != null) {
            Type carouselType = new TypeToken<ArrayList<CarouselItem>>() {}.getType();
            Type newsType = new TypeToken<ArrayList<NewsItem>>() {}.getType();

            List<CarouselItem> cachedCarousel = gson.fromJson(carouselJson, carouselType);
            List<NewsItem> cachedNews = gson.fromJson(newsJson, newsType);

            if (cachedCarousel != null && cachedNews != null &&
                    !cachedCarousel.isEmpty() && !cachedNews.isEmpty()) {

                carouselItems.clear();
                carouselItems.addAll(cachedCarousel);

                newsItems.clear();
                newsItems.addAll(cachedNews);

                return true;
            }
        }
        return false;
    }

    private void clearCache() {
        if (isFragmentDestroyed) return;
        Context context = getContext();
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_CAROUSEL_ITEMS)
                .remove(KEY_NEWS_ITEMS)
                .apply();
    }

    private void processCarousel(Document doc, List<CarouselItem> carouselList) {
        if (doc == null) return;

        Element carousel = doc.getElementById("home_banners_carousel");
        if (carousel == null) return;

        Elements items = carousel.select(".carousel-item");
        for (Element item : items) {
            if (isFragmentDestroyed) return;

            Element linkElem = item.selectFirst("a");
            String linkUrl = linkElem != null ? linkElem.attr("href") : "";
            String imgUrl = item.select("img").attr("src");

            if (!imgUrl.startsWith("http")) {
                imgUrl = "https://www.colegioetapa.com.br" + imgUrl;
            }

            carouselList.add(new CarouselItem(imgUrl, linkUrl));
        }
    }

    private void processNews(Document doc, List<NewsItem> newsList) {
        if (doc == null) return;

        Element newsSection = doc.selectFirst("div.col-12.col-lg-8.mb-5");
        if (newsSection == null) return;

        Elements cards = newsSection.select(".card.border-radius-card");
        cards.removeAll(newsSection.select("#modal-avisos-importantes .card.border-radius-card"));

        for (Element card : cards) {
            String iconUrl = card.select("img.aviso-icon").attr("src");
            String title = card.select("p.text-blue.aviso-text").text();
            String desc = card.select("p.m-0.aviso-text").text();
            String link = card.select("a[target=_blank]").attr("href");

            if (!iconUrl.startsWith("http")) {
                iconUrl = "https://areaexclusiva.colegioetapa.com.br" + iconUrl;
            }

            if (!isDuplicateNews(title, newsList)) {
                newsList.add(new NewsItem(iconUrl, title, desc, link));
            }
        }
    }

    private boolean isDuplicateNews(String title, List<NewsItem> newsList) {
        for (NewsItem ni : newsList) {
            if (ni.getTitle().equals(title)) {
                return true;
            }
        }
        return false;
    }

    private void navigateToWebView(String url) {
        if (isFragmentDestroyed) return;
        WebViewFragment fragment = new WebViewFragment();
        fragment.setArguments(WebViewFragment.createArgs(url));
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showLoadingState() {
        if (isFragmentDestroyed) return;
        handler.post(() -> {
            loadingContainer.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
            layoutSemInternet.setVisibility(View.GONE);
            txtStuckHint.setVisibility(View.VISIBLE);
        });
    }

    private void showContentState() {
        if (isFragmentDestroyed) return;
        handler.post(() -> {
            loadingContainer.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
            layoutSemInternet.setVisibility(View.GONE);
            txtStuckHint.setVisibility(View.GONE);
        });
    }

    private void showOfflineState() {
        if (isFragmentDestroyed) return;
        handler.post(() -> {
            loadingContainer.setVisibility(View.GONE);
            contentContainer.setVisibility(View.GONE);
            layoutSemInternet.setVisibility(View.VISIBLE);
            txtStuckHint.setVisibility(View.GONE);
        });
    }

    private boolean hasInternetConnection() {
        if (isFragmentDestroyed) return false;
        Context context = getContext();
        if (context == null) return false;

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
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

    private void setupCarousel() {
        ViewGroup.LayoutParams params = viewPager.getLayoutParams();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        params.height = (int) (screenWidth * 0.5625); // Proporção 16:9
        viewPager.setLayoutParams(params);

        viewPager.setAdapter(new CarouselAdapter());
        viewPager.setPadding(0, 0, 0, 0);
        viewPager.setClipToPadding(false);
        viewPager.setClipChildren(false);
        viewPager.setOffscreenPageLimit(3);
    }

    private void setupNews() {
        newsRecyclerView.setAdapter(new NewsAdapter());
    }


    private class CarouselAdapter extends androidx.viewpager.widget.PagerAdapter {
        @Override
        public int getCount() {
            return carouselItems.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Context context = container.getContext();
            ImageView imageView = new ImageView(context);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            CarouselItem item = carouselItems.get(position);
            Glide.with(context)
                    .load(item.getImageUrl())
                    .centerCrop()
                    .into(imageView);

            imageView.setOnClickListener(v -> navigateToWebView(item.getLinkUrl()));

            container.addView(imageView);
            return imageView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }
    }

    private class NewsAdapter extends RecyclerView.Adapter<NewsViewHolder> {
        @NonNull
        @Override
        public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.news_item, parent, false);
            return new NewsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
            NewsItem item = newsItems.get(position);
            Glide.with(holder.itemView.getContext())
                    .load(item.getIconUrl())
                    .into(holder.icon);
            holder.title.setText(item.getTitle());
            holder.description.setText(item.getDescription());
            holder.itemView.setOnClickListener(v ->
                    navigateToWebView(item.getLink())
            );
        }

        @Override
        public int getItemCount() {
            return newsItems.size();
        }
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, description;

        NewsViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.news_icon);
            title = view.findViewById(R.id.news_title);
            description = view.findViewById(R.id.news_description);
        }
    }

    static class CarouselItem {
        private String imageUrl;
        private String linkUrl;

        public CarouselItem() {
        }

        public CarouselItem(String imageUrl, String linkUrl) {
            this.imageUrl = imageUrl;
            this.linkUrl = linkUrl;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getLinkUrl() {
            return linkUrl;
        }
    }

    static class NewsItem {
        private String iconUrl;
        private String title;
        private String description;
        private String link;

        public NewsItem() {
        }

        public NewsItem(String iconUrl, String title,
                        String description, String link) {
            this.iconUrl = iconUrl;
            this.title = title;
            this.description = description;
            this.link = link;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getLink() {
            return link;
        }
    }

    public void killFragment() {
        if (getParentFragmentManager().isDestroyed()) return;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        getParentFragmentManager().beginTransaction()
                .remove(this)
                .commitAllowingStateLoss();
    }
}