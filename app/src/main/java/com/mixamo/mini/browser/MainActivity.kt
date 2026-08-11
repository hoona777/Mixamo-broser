package com.mixamo.mini.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001

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
                // تنظیم رزولوشن دسکتاپ و تزریق اسکریپت لمس
                setupDesktopViewport()
                injectTouchScript()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.loadUrl("https://www.mixamo.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // شبیه‌سازی مانیتور استاندارد دسکتاپ
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    }

    // تنظیم رزولوشن استاندارد دسکتاپ (1280px) جهت هماهنگی ابعاد و دایره‌ها
    private fun setupDesktopViewport() {
        val script = """
            (function() {
                let meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=1280, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun injectTouchScript() {
        val script = """
            (function() {
                if (window.__mixamoTouchFixLoaded) return;
                window.__mixamoTouchFixLoaded = true;

                let activeTarget = null;

                function isInteractiveUI(el) {
                    if (!el) return false;
                    return el.closest('input, button, a, select, textarea, label, [role="button"], form, .spectrum-Button, [class*="upload"], [class*="drop"]');
                }

                function dispatchMousePointer(type, touch) {
                    const target = activeTarget || document.elementFromPoint(touch.clientX, touch.clientY);
                    if (!target) return null;

                    const evt = new PointerEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: touch.clientX,
                        clientY: touch.clientY,
                        screenX: touch.screenX,
                        screenY: touch.screenY,
                        pointerId: 1,
                        pointerType: 'mouse',
                        isPrimary: true,
                        buttons: (type === 'pointerup') ? 0 : 1,
                        pressure: (type === 'pointerup') ? 0 : 0.5
                    });

                    target.dispatchEvent(evt);
                    return target;
                }

                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length === 1) {
                        const touch = e.touches[0];
                        const target = document.elementFromPoint(touch.clientX, touch.clientY);
                        
                        if (isInteractiveUI(target)) {
                            activeTarget = null;
                            return;
                        }
                        
                        // قفل کردن نشانه روی بوم 3D یا دایره‌ها
                        activeTarget = dispatchMousePointer('pointerdown', touch);
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (activeTarget && e.touches.length === 1) {
                        e.preventDefault();
                        dispatchMousePointer('pointermove', e.touches[0]);
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (activeTarget) {
                        e.preventDefault();
                        dispatchMousePointer('pointerup', e.changedTouches[0]);
                        activeTarget = null;
                    }
                }, { passive: false });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
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
