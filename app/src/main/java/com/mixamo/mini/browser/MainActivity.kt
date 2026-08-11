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

        // مرورگر دسکتاپ
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        
        // فعال‌سازی اسکرول و زوم آزادانه در تمام صفحات
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    }

    private fun setupDesktopViewport() {
        val script = """
            (function() {
                let meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=1280, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
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

                // تشخیص بوم ۳بعدی میکسامو
                function is3DCanvas(el) {
                    if (!el) return false;
                    return el.tagName.toLowerCase() === 'canvas' || el.closest('canvas');
                }

                function dispatchAllMouseEvents(touch, mouseType, pointerType) {
                    const target = activeTarget || document.elementFromPoint(touch.clientX, touch.clientY);
                    if (!target) return null;

                    const isUp = (mouseType === 'mouseup');
                    
                    const pEvent = new PointerEvent(pointerType, {
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
                        buttons: isUp ? 0 : 1,
                        pressure: isUp ? 0 : 0.5
                    });
                    target.dispatchEvent(pEvent);

                    const mEvent = new MouseEvent(mouseType, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: touch.clientX,
                        clientY: touch.clientY,
                        screenX: touch.screenX,
                        screenY: touch.screenY,
                        buttons: isUp ? 0 : 1,
                        which: 1
                    });
                    target.dispatchEvent(mEvent);

                    return target;
                }

                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length === 1) {
                        const touch = e.touches[0];
                        const target = document.elementFromPoint(touch.clientX, touch.clientY);
                        
                        // فقط اگر روی بوم ۳بعدی زدید لمس متوقف شود، در غیر این صورت صفحه به‌راحتی اسکرول می‌شود
                        if (is3DCanvas(target)) {
                            e.preventDefault();
                            activeTarget = dispatchAllMouseEvents(touch, 'mousedown', 'pointerdown');
                        } else {
                            activeTarget = null;
                        }
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (activeTarget && e.touches.length === 1) {
                        e.preventDefault();
                        dispatchAllMouseEvents(e.touches[0], 'mousemove', 'pointermove');
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (activeTarget) {
                        e.preventDefault();
                        dispatchAllMouseEvents(e.changedTouches[0], 'mouseup', 'pointerup');
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
