package com.mixamo.mini.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
        
        // قابلیت دانلود فایل‌ها
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, 
                URLUtil.guessFileName(url, contentDisposition, mimetype)
            )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
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
        settings.setSupportZoom(true) // فعال‌سازی زوم
        settings.builtInZoomControls = true // نمایش دکمه‌های زوم
        settings.displayZoomControls = false // مخفی کردن کنترل‌های روی صفحه
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

                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length !== 1) return; // اگر ۲ انگشتی (زوم) بود، دخالت نکن
                    const touch = e.touches[0];
                    const target = findBestTarget(touch.clientX, touch.clientY);
                    
                    // بررسی اینکه آیا این یک مفصل است یا نه
                    const isRiggingElement = target.matches('[class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"], [class*="autorig"]');

                    if (isRiggingElement) {
                        e.preventDefault(); // فقط اگر مفصل بود، اسکرول صفحه را متوقف کن
                        activeTarget = target;
                        // کد ارسال ایونت موس... (همان قبلی)
                        fireMouseEvent('mousedown', activeTarget, touch);
                    } else {
                        activeTarget = null; // اگر نبود، بگذار مرورگر عادی کار کند
                    }
                }, { passive: false });

                // (بقیه توابع fireMouseEvent و touchmove/touchend دقیقاً مشابه نسخه قبلی)
                // ...
            })();
        """.trimIndent()
        // ... (بقیه کدهای تزریق)
    }
}
