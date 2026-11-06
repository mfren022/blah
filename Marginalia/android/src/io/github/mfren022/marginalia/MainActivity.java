package io.github.mfren022.marginalia;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Shows the Marginalia web app, which is bundled in the APK under assets/www.
 *
 * Pages are served from https://appassets.androidplatform.net/ (the host Android reserves
 * for this, as used by WebViewAssetLoader), so the app gets a normal https origin: ES
 * modules, fonts and localStorage all behave as they do in a browser. Everything else
 * (Open Library) goes to the network as usual, and outside links open in the browser.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int REQUEST_OPEN = 1;
    private static final int REQUEST_SAVE = 2;
    private static final int DESK_LIGHT = 0xFFD6CFC3;
    private static final int DESK_DARK = 0xFF0E0C0C;

    private WebView web;
    private ValueCallback<Uri[]> pendingOpen;
    private byte[] pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(isNight() ? DESK_DARK : DESK_LIGHT);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        // The app has its own text-size setting; keep its layout predictable.
        settings.setTextZoom(100);
        disableForceDark(settings);

        web.addJavascriptInterface(new Bridge(), "MarginaliaAndroid");
        web.setWebViewClient(new AssetClient());
        web.setWebChromeClient(new FileChooserClient());

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(START_URL);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Dark mode changed: tell the page (it follows the phone unless set otherwise in Settings). */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (web == null) return;
        web.setBackgroundColor(isNight() ? DESK_DARK : DESK_LIGHT);
        web.evaluateJavascript("document.documentElement.setAttribute('data-theme','" + (isNight() ? "dark" : "light") + "')", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (requestCode == REQUEST_OPEN) {
            if (pendingOpen != null) pendingOpen.onReceiveValue(uri != null ? new Uri[] {uri} : null);
            pendingOpen = null;
        } else if (requestCode == REQUEST_SAVE) {
            byte[] bytes = pendingSave;
            pendingSave = null;
            if (uri != null && bytes != null) writeTo(uri, bytes);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Android 10–12 may darken pages automatically; the app has its own dark theme. */
    private static void disableForceDark(WebSettings settings) {
        if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT < 33) {
            try {
                WebSettings.class.getMethod("setForceDark", int.class).invoke(settings, 0 /* FORCE_DARK_OFF */);
            } catch (Exception ignored) {
                // Not available on this WebView.
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            toast("No app can open this link");
        }
    }

    private void writeTo(Uri uri, byte[] bytes) {
        OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IOException("No output stream");
            out.write(bytes);
            toast("Saved");
        } catch (IOException e) {
            toast("Could not save the file");
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Nothing more to do.
                }
            }
        }
    }

    private WebResourceResponse asset(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
        if (path.contains("..")) return notFound();
        String name = "www" + path;
        try {
            InputStream in = getAssets().open(name);
            String mime = mimeType(name);
            boolean text = mime.startsWith("text/") || mime.endsWith("json") || mime.endsWith("xml");
            return new WebResourceResponse(mime, text ? "utf-8" : null, in);
        } catch (IOException e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        WebResourceResponse response = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        response.setStatusCodeAndReasonPhrase(404, "Not Found");
        return response;
    }

    private static String mimeType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".webmanifest")) return "application/manifest+json";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        return "application/octet-stream";
    }

    /* ------------------------------------------------------------------ */
    /* WebView clients                                                     */
    /* ------------------------------------------------------------------ */

    private class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            return HOST.equals(url.getHost()) ? asset(url.getPath()) : null;
        }

        // Android 7+ (API 24) variant; declared without @Override because the build compiles
        // against an older SDK. It still overrides the framework method at run time.
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleNavigation(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(Uri.parse(url));
        }

        private boolean handleNavigation(Uri url) {
            if (HOST.equals(url.getHost())) return false; // stay in the app
            openExternally(url); // Open Library pages and other sites open in the browser
            return true;
        }
    }

    /** Backs <input type="file"> (backup and CSV import) with the system file picker. */
    private class FileChooserClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (pendingOpen != null) pendingOpen.onReceiveValue(null);
            pendingOpen = callback;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQUEST_OPEN);
                return true;
            } catch (ActivityNotFoundException e) {
                pendingOpen = null;
                toast("No app can pick files");
                return false;
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* JavaScript bridge (window.MarginaliaAndroid)                         */
    /* ------------------------------------------------------------------ */

    public class Bridge {
        /** "dark" or "light", following the phone's dark mode. */
        @JavascriptInterface
        public String systemTheme() {
            return isNight() ? "dark" : "light";
        }

        /** Exports (backups, CSV, chart images): ask where to save, then write the bytes. */
        @JavascriptInterface
        public void saveFile(final String name, final String mimeType, final String base64) {
            final byte[] bytes;
            try {
                bytes = Base64.decode(base64, Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pendingSave = bytes;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                    intent.putExtra(Intent.EXTRA_TITLE, name);
                    try {
                        startActivityForResult(intent, REQUEST_SAVE);
                    } catch (ActivityNotFoundException e) {
                        pendingSave = null;
                        toast("No app can save files");
                    }
                }
            });
        }

        /** Colour the status and navigation bars to match the book's cover. */
        @JavascriptInterface
        public void setBarColors(final String hex) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int color = Color.parseColor(hex);
                        getWindow().setStatusBarColor(color);
                        getWindow().setNavigationBarColor(color);
                    } catch (IllegalArgumentException ignored) {
                        // Not a colour we understand; keep the current bars.
                    }
                }
            });
        }
    }
}
