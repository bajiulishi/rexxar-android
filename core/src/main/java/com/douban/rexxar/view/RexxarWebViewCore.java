package com.douban.rexxar.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

import com.douban.rexxar.Constants;
import com.douban.rexxar.Rexxar;
import com.douban.rexxar.resourceproxy.cache.CacheEntry;
import com.douban.rexxar.resourceproxy.cache.CacheHelper;
import com.douban.rexxar.resourceproxy.network.HtmlHelper;
import com.douban.rexxar.resourceproxy.network.RexxarContainerAPI;
import com.douban.rexxar.route.Route;
import com.douban.rexxar.route.RouteManager;
import com.douban.rexxar.utils.LogUtils;
import com.douban.rexxar.utils.RxLoadError;
import com.douban.rexxar.utils.Utils;
import com.douban.rexxar.utils.WebViewCompatUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * route doLoadCache
 * 设置client
 * 协议
 *
 * Created by luanqian on 15/10/28.
 */
public class RexxarWebViewCore extends SafeWebView {

    public static final String TAG = RexxarWebViewCore.class.getSimpleName();

    /**
     *
     */
    public interface UriLoadCallback {

        /**
         * 开始load uri
         */
        boolean onStartLoad();

        /**
         * 开始下载html
         */
        boolean onStartDownloadHtml();

        /**
         * load成功
         */
        boolean onSuccess();

        /**
         * load失败
         * @param error
         */
        boolean onFail(RxLoadError error);
    }

    public static class SimpleUriLoadCallback implements UriLoadCallback {

        @Override
        public boolean onStartLoad() {
            return false;
        }

        @Override
        public boolean onStartDownloadHtml() {
            return false;
        }

        @Override
        public boolean onSuccess() {
            return false;
        }

        @Override
        public boolean onFail(RxLoadError error) {
            return false;
        }
    }

    public interface WebCallbacks {
        void onPageLoadStarted(String url);
        void onPageLoadFinished(String url);
        void onDraw();
    }

    public WeakReference<WebCallbacks> mWebCallback = null;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private RexxarWebViewClient mWebViewClient;
    private RexxarWebChromeClient mWebChromeClient;

    public RexxarWebViewCore(Context context) {
        super(context);
        setup();
    }

    public RexxarWebViewCore(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public RexxarWebViewCore(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    private void setup() {
        setBackgroundColor(Color.WHITE);
        WebSettings ws = getSettings();
        setupWebSettings(ws);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setWebContentsDebuggingEnabled(Rexxar.DEBUG);
        }
        if (null == mWebViewClient) {
            mWebViewClient = new RexxarWebViewClient();
        }
        setWebViewClient(mWebViewClient);
        if (null == mWebChromeClient) {
            mWebChromeClient = new RexxarWebChromeClient();
        }
        setWebChromeClient(mWebChromeClient);
        setDownloadListener(getDownloadListener());
    }

    @TargetApi(16)
    @SuppressLint("SetJavaScriptEnabled")
    protected void setupWebSettings(WebSettings ws) {
        ws.setAppCacheEnabled(true);
        WebViewCompatUtils.enableJavaScriptForWebView(getContext(), ws);
        ws.setJavaScriptEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        ws.setAllowFileAccess(true);
        if (Utils.hasJellyBean()) {
            ws.setAllowFileAccessFromFileURLs(true);
            ws.setAllowUniversalAccessFromFileURLs(true);
        }

        // enable html cache
        ws.setDomStorageEnabled(true);
        ws.setAppCacheEnabled(true);
        // Set cache size to 8 mb by default. should be more than enough
        ws.setAppCacheMaxSize(1024 * 1024 * 8);
        // This next one is crazy. It's the DEFAULT location for your app's cache
        // But it didn't work for me without this line
        ws.setAppCachePath("/data/data/" + getContext().getPackageName() + "/cache");
        ws.setAllowFileAccess(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        String ua = ws.getUserAgentString() + " " + Rexxar.getUserAgent();
        ws.setUserAgentString(ua);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            ws.setUseWideViewPort(true);
        }

        if (Utils.hasLollipop()) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    protected DownloadListener getDownloadListener() {
        return new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.parse(url);
                intent.setData(uri);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    getContext().startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    /**
     * 自定义url拦截处理
     *
     * @param widget
     */
    public void addRexxarWidget(RexxarWidget widget) {
        if (null == widget) {
            return;
        }
        mWebViewClient.addRexxarWidget(widget);
    }

    /**
     * 自定义container api
     *
     * @param containerAPI
     */
    public void addContainerApi(RexxarContainerAPI containerAPI) {
        if (null != containerAPI) {
            mWebViewClient.addContainerApi(containerAPI);
        }
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        if (!(client instanceof RexxarWebViewClient)) {
            throw new IllegalArgumentException("client must inherit RexxarWebViewClient");
        }
        if (null != mWebViewClient) {
            for (RexxarWidget widget : mWebViewClient.getRexxarWidgets()) {
                if (null != widget) {
                    ((RexxarWebViewClient) client).addRexxarWidget(widget);
                }
            }
            for (RexxarContainerAPI api : mWebViewClient.getRexxarContainerApis()) {
                if (null != api) {
                    ((RexxarWebViewClient) client).addContainerApi(api);
                }
            }
        }
        mWebViewClient = (RexxarWebViewClient) client;
        super.setWebViewClient(client);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        if (!(client instanceof RexxarWebChromeClient)) {
            throw new IllegalArgumentException("client must inherit RexxarWebViewClient");
        }
        mWebChromeClient = (RexxarWebChromeClient) client;
        super.setWebChromeClient(client);
    }

    /**
     * Load Page
     *
     * @param uri
     */
    public void loadUri(String uri) {
        loadUri(uri, null);
    }

    /**
     * Load Page
     *
     * @param uri
     * @param callback
     */
    public void loadUri(String uri, UriLoadCallback callback) {
        loadUri(uri, callback, true);
    }

    /**
     * Load Part
     *
     * @param uri
     */
    public void loadPartialUri(String uri) {
        loadUri(uri, null);
    }

    /**
     * Load Part
     *
     * @param uri
     * @param callback
     */
    public void loadPartialUri(String uri, UriLoadCallback callback) {
        loadUri(uri, callback, false);
    }

    /**
     * Rexxar entry
     * <p>
     * 如果map能够匹配上，则
     */
    private void loadUri(final String uri, final UriLoadCallback callback, final boolean page) {
        LogUtils.i(TAG, "loadUri , uri = " + (null != uri ? uri : "null"));
        if (TextUtils.isEmpty(uri)) {
            throw new IllegalArgumentException("[RexxarWebView] [loadUri] uri can not be null");
        }
        final Route route;
        if (page) {
            route = RouteManager.getInstance().findRoute(uri);
        } else {
            route = RouteManager.getInstance().findPartialRoute(uri);
        }
        if (null == route) {
            LogUtils.i(TAG, "route not found");
            RouteManager.getInstance().refreshRoute(new RouteManager.RouteRefreshCallback() {
                @Override
                public void onSuccess(String data) {
                    Route temp = page ? RouteManager.getInstance().findRoute(uri) : RouteManager.getInstance().findPartialRoute(uri);
                    if (null == temp) {
                        // 没有找到通知外面route找不到
                        if (null != callback) {
                            callback.onFail(RxLoadError.ROUTE_NOT_FOUND);
                        }
                    } else {
                        // 如果找到重新加载
                        loadUri(uri, callback, page);
                    }
                }

                @Override
                public void onFail() {
                    if (null != callback) {
                        callback.onFail(RxLoadError.ROUTE_NOT_FOUND);
                    }
                }

                @Override
                public void onHtmlFileCacheFail(String cause) {
                    if (null != callback) {
                        callback.onFail(RxLoadError.ROUTE_NOT_FOUND);
                    }
                }
            });
        } else {
            if (null != callback) {
                callback.onStartLoad();
            }
            if (CacheHelper.getInstance().cacheEnabled() && CacheHelper.getInstance().hasHtmlCached(route.getHtmlFile())) {
                // show cache
                doLoadCache(uri, route);
                if (null != callback) {
                    callback.onSuccess();
                }
            } else {
                if (null != callback) {
                    callback.onStartDownloadHtml();
                }
                HtmlHelper.prepareHtmlFile(route.getHtmlFile(), new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        doLoadRemote(uri, route);
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (response.isSuccessful()) {
                                    LogUtils.i(TAG, "download success");
                                    final CacheEntry cacheEntry = CacheHelper.getInstance().findHtmlCache(route.getHtmlFile());
                                    if (null != cacheEntry && cacheEntry.isValid()) {
                                        // show cache
                                        doLoadCache(uri, route);
                                        if (null != callback) {
                                            callback.onSuccess();
                                        }
                                        return;
                                    }
                                }
                                doLoadRemote(uri, route);
                            }
                        });
                    }
                });
            }
        }
    }

    private void doLoadCache(String uri, Route route) {
        LogUtils.i(TAG, "file cache , doLoadCache cache file");
        // using file schema to doLoadCache
        loadUrl(Constants.FILE_AUTHORITY + route.getHtmlFile() + "?uri=" + Uri.encode(uri));
    }

    public void doLoadRemote(String uri, Route route) {
        LogUtils.i(TAG, "route file , doLoadRemote remote file");
        loadUrl(route.getHtmlFile() + "?uri=" + Uri.encode(uri));
    }

    public interface WebViewHeightCallback {
        void onHeightChange(int height);
    }

    public void addWebViewHeightCallback(WebViewHeightCallback callback) {
        if (null != callback) {
            mCallback = callback;
        }
    }

    // 上次计算的内容高度，用于监测WebView高度变化
    int mLastContentHeight;
    // 页面是否加载完
    protected boolean mLoadFinished = false;
    Handler mHandler = new Handler(Looper.getMainLooper());
    // 计算WebView高度的runnable
    private Runnable mWebViewHeightRunnable;
    // 需要重新设置webview的高度
    public boolean mExpandContentHeight = false;
    WebViewHeightCallback mCallback;

    /**
     * webview撑满
     * @param enable
     */
    public void enableExpandContentHeight(boolean enable) {
        this.mExpandContentHeight = enable;
    }

    public interface WebViewScrollListener {
        void onScrollChanged(int l, int t, int oldl, int oldt);
    }

    WeakReference<WebViewScrollListener> mWebViewScrollListener;

    public void setWebViewScrollListener(WebViewScrollListener scrollListener) {
        if (null != scrollListener) {
            mWebViewScrollListener = new WeakReference<>(scrollListener);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (null != mWebViewScrollListener && null != mWebViewScrollListener.get()) {
            mWebViewScrollListener.get().onScrollChanged(l, t, oldl, oldt);
        }
    }

    public interface ReloadDelegate {
        void onReload();
    }

    public WeakReference<ReloadDelegate> mReloadDelegateReference;

    public void setReloadDelegate(ReloadDelegate reloadDelegate) {
        if (null != reloadDelegate) {
            mReloadDelegateReference = new WeakReference<>(reloadDelegate);
        }
    }

    @Override
    public void reload() {
        if (null != mReloadDelegateReference && null != mReloadDelegateReference.get()) {
            mReloadDelegateReference.get().onReload();
            return;
        }
        super.reload();
    }

    /**
     * 获取webview内容高度
     * @return
     */
    public int getWebViewContentHeight() {
        if (mLoadFinished) {
            return computeVerticalScrollRange();
        }
        return 0;
    }

    /**
     * 调整webview的高度
     */
    public void resizeWebView() {
        if (null == mWebViewHeightRunnable) {
            mWebViewHeightRunnable = new Runnable() {
                @Override
                public void run() {
                    // 重新测量webview,之后`getMeasuredHeight`的值才能准确
                    measure(0, 0);
                    int height = getMeasuredHeight();
                    setWebViewHeight(height);
                }
            };
        }
        // 移除之前的设置
        mHandler.removeCallbacks(mWebViewHeightRunnable);
        // 重新设置定时
        mHandler.postDelayed(mWebViewHeightRunnable, 300);
    }


    public void setWebviewCallback(WebCallbacks callback) {
        mWebCallback = new WeakReference<WebCallbacks>(callback);
    }

    /**
     * 设置webview高度
     * @param height in pixel
     */
    private void setWebViewHeight(int height) {
        // 优先用callback
        if (null != mCallback) {
            mCallback.onHeightChange(height);
        }
        // webview core
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (null == layoutParams) {
            layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        } else {
            layoutParams.height = height;
        }
        setLayoutParams(layoutParams);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        /**
         * 当页面加载完毕后，监测webview高度发生变化时重新调整webview高度
         */
        if (mLoadFinished && mExpandContentHeight) {
            int contentHeight = getContentHeight();
            if (contentHeight != mLastContentHeight) {
                mLastContentHeight = contentHeight;
                // 重新获取高度
                resizeWebView();
            }
        }
        if (mLoadFinished) {
            if (null != mWebCallback && null != mWebCallback.get()) {
                mWebCallback.get().onDraw();
            }
        }
    }
}
