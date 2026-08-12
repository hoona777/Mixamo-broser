package com.mixamo.mini.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
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
        
        // سیستم پیشرفته دانلود فایل‌ها مستقیم به پوشه Downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)

                request.setMimeType(mimetype)
                // اضافه کردن کوکی‌ها برای پشتیبانی از دانلود حساب کاربری میکسیمو
                request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("در حال دانلود فایل...")
                request.setTitle(fileName)
                
                // اعلان دانلود در بالای صفحه گوشی
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // ذخیره مستقیم در پوشه عمومی Downloads
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                Toast.makeText(applicationContext, "دانلود شروع شد: $fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "خطا در دانلود: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                injectTouchScript()
            }
        }
        
        webView.loadUrl("https://www.mixamo.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    private fun injectTouchScript() {
        val script = """
            (function() {
                if (window.__mixamoTouchFixInjected) return;
                window.__mixamoTouchFixInjected = true;

                let activeTarget = null;

                function findBestTarget(x, y) {
                    const el = document.elementFromPoint(x, y);
                    if (!el) return null;
                    const marker = el.closest('[class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"], [class*="autorig"]');
                    return marker || el;
                }

                function fireMouseEvent(type, target, touch) {
                    const evt = new MouseEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: touch.clientX,
                        clientY: touch.clientY,
                        screenX: touch.screenX,
                        screenY: touch.screenY,
                        button: 0
                    });
                    target.dispatchEvent(evt);
                }

                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length !== 1) return;
                    const touch = e.touches[0];
                    const target = findBestTarget(touch.clientX, touch.clientY);
                    
                    const isRiggingElement = target && target.matches && target.matches('[class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"], [class*="autorig"]');

                    if (isRiggingElement) {
                        e.preventDefault();
                        activeTarget = target;
                        fireMouseEvent('mousedown', activeTarget, touch);
                    } else {
                        activeTarget = null;
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (!activeTarget || e.touches.length !== 1) return;
                    e.preventDefault();
                    const touch = e.touches[0];
                    fireMouseEvent('mousemove', activeTarget, touch);
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (!activeTarget) return;
                    const touch = e.changedTouches[0];
                    fireMouseEvent('mouseup', activeTarget, touch);
                    activeTarget = null;
                }, { passive: false });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }
}
