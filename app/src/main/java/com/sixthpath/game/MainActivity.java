package com.sixthpath.game;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

/**
 * SIXTH PATH, as a self-contained application.
 *
 * ---- WHY THE ASSETS ARE SERVED, NOT OPENED ---------------------------------
 * The obvious approach — loadUrl("file:///android_asset/index.html") — cannot
 * run this game. The whole thing is ES modules behind an import map, and the
 * file:// scheme gives every file a null origin: module imports are refused as
 * cross-origin, and localStorage (every save, every profile) throws on access.
 *
 * WebViewAssetLoader answers requests on a virtual https origin
 * (appassets.androidplatform.net) out of the APK's own assets. The page then
 * behaves exactly as it does on sixthpath.com — modules resolve, the import map
 * applies, storage persists — while nothing leaves the device and no network is
 * touched. This is also why the app runs with no Chrome installed: it uses the
 * system WebView component, which every Android device carries.
 */
public class MainActivity extends Activity {

    private WebView web;
    private FrameLayout kok;          // web + splash, in that order
    private View acilis;              // the mark shown until the page reports ready
    private long geriAn = 0;          // when back was last pressed at the root

    /** Any uncaught throwable becomes a readable screen (CrashActivity). */
    private void armCrashScreen() {
        final Thread.UncaughtExceptionHandler eski = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter w = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(w));
                android.content.Intent i = new android.content.Intent(this, CrashActivity.class);
                i.putExtra("stack", w.toString());
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            } catch (Throwable ignore) { }
            if (eski != null) eski.uncaughtException(t, e);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    /**
     * THE ONE THING THE PAGE TELLS THE SHELL. Not an API surface — a single
     * word, said once, when the console has actually painted. Everything else
     * the app needs to know it can ask for.
     */
    private class Kopru {
        @JavascriptInterface
        public void hazir() {
            runOnUiThread(() -> splashKaldir());
        }
    }

    /** Fade the mark out and drop it. Idempotent: the page may say ready twice. */
    private void splashKaldir() {
        final View v = acilis;
        if (v == null) return;
        acilis = null;
        try {
            v.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                try { if (kok != null) kok.removeView(v); } catch (Throwable ignore) { }
            }).start();
        } catch (Throwable ignore) {
            try { if (kok != null) kok.removeView(v); } catch (Throwable ignore2) { }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        armCrashScreen();

        // the game paints its own black; the window must not flash white under it
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        // Android 15+ (API 35) is edge-to-edge by force and these two setters are
        // deprecated no-ops there (Play Console, 2026-09-19); the bars are hidden
        // anyway and the window ground is already the game's black.
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(Color.parseColor("#0b0a07"));
            getWindow().setNavigationBarColor(Color.parseColor("#0b0a07"));
        }

        // THE SCREEN STAYS AWAKE WHILE THE GAME IS IN FRONT, AND ONLY THEN.
        // The flag was added once and never cleared, so it held the display on
        // for the life of the activity. Paired with a page that (until the
        // stillness went in) animated forever, that is a phone that never gets
        // to idle — the owner's "telefon aşırı ısınıyor". The flag now follows
        // the lifecycle: set in onResume, cleared in onPause, so a backgrounded
        // game costs nothing.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        // A DISABLED SYSTEM WEBVIEW SAYS SO (the one constructor that can throw
        // before the first frame: Android System WebView disabled or mid-update
        // kills the app with nothing on screen otherwise).
        try {
            web = new WebView(this);
        } catch (Throwable t) {
            TextViewFallback(t);
            return;
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage: saves, profile, meta
        s.setAllowFileAccess(false);           // nothing outside the APK is readable
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);   // the sfx layer starts itself
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);                    // the type scale is the design's, not the system's
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);      // assets are local; caching them twice is waste
        web.setBackgroundColor(Color.parseColor("#0b0a07"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // THE WEBVIEW DRAWS INTO ITS OWN HARDWARE LAYER, AND ITS RENDERER IS NOT
        // TRIMMED (owner, 2026-09-19, three screen recordings: the card went black
        // in tiles while being dragged — perfect in Chrome, broken here). A WebView
        // drawn inline into the view hierarchy gets a far smaller tile budget than
        // Chrome and drops tiles under pressure; its own layer composites whole
        // frames, and an IMPORTANT renderer is not asked to give memory back while
        // the game is in front.
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try { web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false); } catch (Throwable ignore) { }
        }

        // A WEBVIEW WITH NO CHROME CLIENT ANSWERS confirm() WITH false AND
        // DRAWS NOTHING. Every button behind a confirmation was therefore dead
        // in the packaged app while working in a browser (owner, 2026-08-25:
        // "yeniden baslat tusu calismiyor"). The game now asks with its own
        // in-page modal, so nothing depends on this any more — but a WebView
        // without one is a trap for the next dialog somebody adds.
        web.setWebChromeClient(new WebChromeClient());

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            /** THE RENDERER'S DEATH IS NOT THE APP'S. On targetSdk 26+ a dead
             *  render process kills the whole app unless this returns true —
             *  and a low-RAM phone can lose the renderer on the very first
             *  paint of an 18MB page, which reads exactly as "başlamadan
             *  kapanıyor". The view is rebuilt once; the game reboots from its
             *  own save. */
            @Override
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                try {
                    android.view.ViewGroup parent = (android.view.ViewGroup) v.getParent();
                    if (parent != null) parent.removeView(v);
                    v.destroy();
                } catch (Throwable ignore) { }
                web = null;
                recreate();
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                // everything inside the virtual origin is the game and stays here;
                // a real outward link is not the app's business to render
                return !"appassets.androidplatform.net".equals(u.getHost());
            }
        });

        web.addJavascriptInterface(new Kopru(), "SPNative");

        // THE MARK STAYS UP UNTIL THE CONSOLE IS ON. The window background paints
        // the ground colour from the instant the icon is tapped; this puts the
        // mark on that ground and holds it over the WebView while the page boots,
        // so the launch is a held image rather than a black wait.
        kok = new FrameLayout(this);
        kok.setBackgroundColor(Color.parseColor("#0b0a07"));
        kok.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.mipmap.ic_launcher);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout kap = new FrameLayout(this);
        kap.setBackgroundColor(Color.parseColor("#0b0a07"));
        int boy = (int) (getResources().getDisplayMetrics().density * 108);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(boy, boy);
        lp.gravity = android.view.Gravity.CENTER;
        kap.addView(mark, lp);
        kok.addView(kap, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        acilis = kap;

        setContentView(kok);
        applyImmersive();
        web.loadUrl("https://appassets.androidplatform.net/index.html");

        // A PAGE THAT NEVER REPORTS READY MUST NOT KEEP ITS OWN MARK ON SCREEN.
        // The bridge is the fast path; this is the floor under it.
        web.postDelayed(this::splashKaldir, 6000);
    }

    /**
     * A CONSOLE FILLS THE SCREEN — applied AFTER setContentView, never before.
     * The owner's first phone crash report proved the order matters:
     * getInsetsController() before the decor exists NPEs inside PhoneWindow
     * (getWindowInsetsController() on a null DecorView, Android 12/OEM).
     * setContentView installs the decor; only then may the bars be hidden.
     * Wrapped anyway: losing the immersive bars must never cost the game.
     */
    private void applyImmersive() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Throwable ignore) { }
    }

    /** The WebView could not even be constructed: say it, in words. */
    private void TextViewFallback(Throwable t) {
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setTextColor(Color.parseColor("#e0dcd3"));
        tv.setBackgroundColor(Color.parseColor("#0b0a07"));
        tv.setPadding(36, 72, 36, 72);
        tv.setText("SIXTH PATH açılamadı: sistem WebView bileşeni kullanılamıyor.\n\n"
                + "Ayarlar → Uygulamalar → Android System WebView etkin mi bakın, "
                + "sonra uygulamayı yeniden açın.\n\n" + t);
        setContentView(tv);
    }

    /**
     * BACK CLOSES WHAT IS ON TOP OF YOU. It used to ask web.canGoBack(), which
     * for a single-page game is always false — so back quit the app from the
     * middle of the map, from an open palace sheet, from anywhere. The shell
     * cannot know which layer is on top and should not have to: the page peels
     * it and says whether it peeled anything. Only when nothing is on top does
     * this become "leave", and then it asks first, once.
     */
    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        try {
            web.evaluateJavascript(
                "(function(){try{return !!(window.__spGeri && window.__spGeri());}catch(e){return false;}})()",
                deger -> {
                    if ("true".equals(deger)) return;         // a layer was closed
                    long simdi = System.currentTimeMillis();
                    if (simdi - geriAn < 2000) { finish(); return; }
                    geriAn = simdi;
                    try {
                        Toast.makeText(this, R.string.cikmak_icin, Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignore) { }
                });
        } catch (Throwable t) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // a game nobody is looking at neither renders nor holds the screen on
        try { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable ignore) { }
        if (web != null) { web.onPause(); web.pauseTimers(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Throwable ignore) { }
        if (web != null) { web.resumeTimers(); web.onResume(); }
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
