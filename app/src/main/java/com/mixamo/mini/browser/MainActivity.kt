package com.mixamo.mini.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebViewSettings()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                // تزریق اسکریپت لمس خودکار
                injectTouchScript()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }
        }

        // بارگذاری مستقیم سایت میکسامو
        webView.loadUrl("https://www.mixamo.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        
        // شبیه‌سازی سیستم‌عامل و مرورگر دسکتاپ برای اجرای صحیح گرافیک 3D (WebGL)
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        
        // فعال‌سازی زوم خودکار
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    }

    private fun injectTouchScript() {
        val script = """
            (function() {
                if (window.__mixamoTouchFixLoaded) return;
                window.__mixamoTouchFixLoaded = true;

                let activeTarget = null;

                function createPointerEvent(type, touch) {
                    const el = activeTarget || document.elementFromPoint(touch.clientX, touch.clientY);
                    if (!el) return null;

                    const evt = new PointerEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: touch.clientX,
                        clientY: touch.clientY,
                        pointerId: 1,
                        pointerType: 'mouse',
                        isPrimary: true,
                        buttons: (type === 'pointerup') ? 0 : 1
                    });
                    el.dispatchEvent(evt);
                    return el;
                }

                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length === 1) {
                        const touch = e.touches[0];
                        activeTarget = createPointerEvent('pointerdown', touch);
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (activeTarget && e.touches.length === 1) {
                        e.preventDefault();
                        createPointerEvent('pointermove', e.touches[0]);
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (activeTarget) {
                        e.preventDefault();
                        createPointerEvent('pointerup', e.changedTouches[0]);
                        activeTarget = null;
                    }
                }, { passive: false });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) {
            // اسکریپت با موفقیت اعمال شد
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
