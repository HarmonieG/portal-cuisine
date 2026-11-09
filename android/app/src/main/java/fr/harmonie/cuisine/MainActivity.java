package fr.harmonie.cuisine;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Deux navigateurs superposés :
 *  - "home" : ta page GitHub Pages (accueil, minuteurs). Toujours chargée, donc les minuteurs sonnent même si tu es sur une recette.
 *  - "ext"  : Cookidoo, YouTube Music... avec une barre Retour / Accueil / Recharger toujours visible.
 */
public class MainActivity extends Activity {

    static final String HOME_URL = "https://harmonieg.github.io/portal-cuisine/";
    static final String HOME_HOST = "harmonieg.github.io";

    private WebView home;
    private WebView ext;
    private LinearLayout bar;
    private boolean clearExtHistory = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF16201B);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        home = makeWebView(true);
        ext = makeWebView(false);
        content.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(ext, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ext.setVisibility(View.GONE);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF16201B);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.addView(makeButton("‹  Retour", new View.OnClickListener() {
            @Override public void onClick(View v) { goBack(); }
        }), buttonParams());
        bar.addView(makeButton("Accueil", new View.OnClickListener() {
            @Override public void onClick(View v) { showExt(false); }
        }), buttonParams());
        bar.addView(makeButton("Recharger", new View.OnClickListener() {
            @Override public void onClick(View v) { ext.reload(); }
        }), buttonParams());
        bar.setVisibility(View.GONE);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));

        setContentView(root);
        home.loadUrl(HOME_URL);
    }

    private WebView makeWebView(final boolean isHome) {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(isHome ? 0xFFEEF1EC : Color.WHITE);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(!isHome);
        // Enlève la marque "; wv" pour que les sites traitent l'appli comme un vrai Chrome.
        String ua = s.getUserAgentString();
        if (ua != null) {
            s.setUserAgentString(ua.replace("; wv", "").replace("Version/4.0 ", ""));
        }
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    return true; // ignore intent:, market:, etc.
                }
                if (isHome) {
                    if (HOME_HOST.equals(uri.getHost())) return false;
                    openExt(uri.toString());
                    return true;
                }
                return false; // reste dans le navigateur externe
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isHome && clearExtHistory) {
                    clearExtHistory = false;
                    view.clearHistory();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isHome && request.isForMainFrame()) {
                    view.loadDataWithBaseURL(HOME_URL,
                            "<body style='font-family:sans-serif;font-size:36px;padding:60px;background:#EEF1EC'>"
                                    + "<p>Impossible de charger la page.</p>"
                                    + "<p><a href='" + HOME_URL + "'>R&eacute;essayer</a></p></body>",
                            "text/html", "utf-8", null);
                }
            }
        });
        return wv;
    }

    private Button makeButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(22);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF2F3D36);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(dp(6), 0, dp(6), 0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void openExt(String url) {
        clearExtHistory = true;
        ext.loadUrl(url);
        showExt(true);
    }

    private void showExt(boolean show) {
        ext.setVisibility(show ? View.VISIBLE : View.GONE);
        bar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void goBack() {
        if (ext.getVisibility() == View.VISIBLE) {
            if (ext.canGoBack()) ext.goBack();
            else showExt(false);
        } else if (home.canGoBack()) {
            home.goBack();
        }
    }

    @Override
    public void onBackPressed() {
        goBack(); // jamais de sortie de l'appli par erreur
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
