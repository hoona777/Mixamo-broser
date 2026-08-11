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

        // تنظیم مرورگر دسکتاپ
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        
        // فعال‌سازی اسکرول و زوم استاندارد اندروید
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    }

    private fun injectTouchScript() {
        val script = """
            (function() {
                if (window.__mixamoTouchFixInjected) return;
                window.__mixamoTouchFixInjected = true;

                let activeTarget = null;

                // پیدا کردن دقیق دایره‌ها حتی با لمس غیردقیق انگشت
                function findBestTarget(x, y) {
                    let el = document.elementFromPoint(x, y);
                    if (!el) return null;

                    if (el.closest) {
                        const marker = el.closest('[class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"], svg, canvas');
                        if (marker) return marker;
                    }

                    // جستجو در شعاع ۱۵ پیکسلی اطراف نقطه‌ی لمس شده
                    const offsets = [
                        [0, 0], [0, -10], [0, 10], [-10, 0], [10, 0],
                        [-15, -15], [15, -15], [-15, 15], [15, 15]
                    ];

                    for (let [dx, dy] of offsets) {
                        let candidate = document.elementFromPoint(x + dx, y + dy);
                        if (candidate && candidate.closest) {
                            let match = candidate.closest('[class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"]');
                            if (match) return match;
                        }
                    }

                    return el;
                }

                function fireMouseEvent(type, target, touch) {
                    if (!target) return;

                    const isUp = (type === 'mouseup' || type === 'pointerup');
                    const clientX = touch.clientX;
                    const clientY = touch.clientY;
                    const pageX = touch.pageX || (clientX + window.scrollX);
                    const pageY = touch.pageY || (clientY + window.scrollY);

                    const eventInit = {
                        bubbles: true,
                        cancelable: true,
                        composed: true,
                        view: window,
                        detail: 1,
                        screenX: touch.screenX || clientX,
                        screenY: touch.screenY || clientY,
                        clientX: clientX,
                        clientY: clientY,
                        pageX: pageX,
                        pageY: pageY,
                        button: 0,
                        buttons: isUp ? 0 : 1,
                        which: 1,
                        pointerId: 1,
                        pointerType: 'mouse',
                        isPrimary: true,
                        pressure: isUp ? 0 : 0.5
                    };

                    if (type.startsWith('pointer')) {
                        const pe = new PointerEvent(type, eventInit);
                        target.dispatchEvent(pe);
                    } else {
                        const me = new MouseEvent(type, eventInit);
                        target.dispatchEvent(me);
                    }
                }

                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length !== 1) return;

                    const touch = e.touches[0];
                    const target = findBestTarget(touch.clientX, touch.clientY);

                    if (!target) return;

                    const isRiggingElement = target.closest('canvas, svg, [class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"], [class*="autorig"]');

                    if (isRiggingElement) {
                        activeTarget = target;
                        e.preventDefault();

                        fireMouseEvent('pointerdown', activeTarget, touch);
                        fireMouseEvent('mousedown', activeTarget, touch);
                    } else {
                        activeTarget = null;
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (activeTarget && e.touches.length === 1) {
                        e.preventDefault();
                        const touch = e.touches[0];

                        fireMouseEvent('pointermove', activeTarget, touch);
                        fireMouseEvent('mousemove', activeTarget, touch);
                        fireMouseEvent('mousemove', document, touch);
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (activeTarget) {
                        e.preventDefault();
                        const touch = e.changedTouches[0];

                        fireMouseEvent('pointerup', activeTarget, touch);
                        fireMouseEvent('mouseup', activeTarget, touch);
                        fireMouseEvent('click', activeTarget, touch);

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
