package fr.harmonie.cuisine;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

/**
 * Deux navigateurs superposés :
 *  - "home" : ta page GitHub Pages (accueil, minuteurs). Toujours chargée, donc les minuteurs sonnent même si tu es sur une recette.
 *  - "ext"  : Cookidoo, YouTube Music... avec une barre en haut (même style que la page) : Retour / Accueil / Recharger.
 */
public class MainActivity extends Activity {

    static final String HOME_URL = "https://harmonieg.github.io/portal-cuisine/";
    static final String HOME_HOST = "harmonieg.github.io";

    private static final int GROUND = 0xFFEEF1EC;
    private static final int INK = 0xFF16201B;
    private static final int MUTE = 0xFF5B665F;

    private WebView home;
    private WebView ext;
    private LinearLayout bar;
    private boolean clearExtHistory = false;

    /** Appelé par la page d'accueil (bouton "Quitter"). */
    public class Bridge {
        @JavascriptInterface
        public void quit() {
            runOnUiThread(new Runnable() {
                @Override public void run() { finishAndRemoveTask(); }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(GROUND);

        // Barre du haut, dans le style de la page (mêmes marges, mêmes boutons blancs arrondis)
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(GROUND);
        bar.setPadding(dp(48), dp(28), dp(48), dp(28));
        bar.addView(makePill("‹  Retour", new View.OnClickListener() {
            @Override public void onClick(View v) { goBack(); }
        }));
        bar.addView(makePill("Accueil", R.drawable.ic_home, new View.OnClickListener() {
            @Override public void onClick(View v) { showExt(false); }
        }));
        bar.addView(makePill("Recharger", R.drawable.ic_refresh, new View.OnClickListener() {
            @Override public void onClick(View v) { ext.reload(); }
        }));
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(40);
        clock.setTextColor(MUTE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        bar.addView(clock);
        bar.setVisibility(View.GONE);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        home = makeWebView(true);
        ext = makeWebView(false);
        home.addJavascriptInterface(new Bridge(), "Android");
        content.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(ext, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ext.setVisibility(View.GONE);

        setContentView(root);
        home.loadUrl(HOME_URL);
    }

    private WebView makeWebView(final boolean isHome) {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(isHome ? GROUND : Color.WHITE);
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

    /** Bouton blanc arrondi avec icône, comme le bouton "Accueil" de la page. */
    private Button makePill(String label, int iconRes, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(26);
        b.setAllCaps(false);
        b.setTextColor(INK);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(10));
        b.setPadding(dp(24), 0, dp(34), 0);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setBackgroundTintList(null);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, pillBg(0xFFE3E8E2));
        bg.addState(new int[]{}, pillBg(Color.WHITE));
        b.setBackground(bg);
        b.setOnClickListener(l);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(80));
        p.setMargins(0, 0, dp(16), 0);
        b.setLayoutParams(p);
        return b;
    }

    /** Pastille + ombre fine de 2 px dessous, comme sur la page. */
    private Drawable pillBg(int color) {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{pillShape(0x14000000), pillShape(color)});
        ld.setLayerInset(1, 0, 0, 0, dp(2));
        return ld;
    }

    private GradientDrawable pillShape(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(40));
        return g;
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
        goBack(); // jamais de sortie de l'appli par erreur : on quitte avec le bouton de l'accueil
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
