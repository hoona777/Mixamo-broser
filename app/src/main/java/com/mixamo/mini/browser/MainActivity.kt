package com.mixamo.mini.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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

    // متغیرهای مدیریت انتخاب فایل برای آپلود
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        setupWebViewSettings()

        // سیستم دانلود مستقیم فایل‌ها به پوشه Downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)

                request.setMimeType(mimetype)
                request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("در حال دانلود فایل...")
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
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

        // فعال‌سازی قطعی فایل‌مانجر اندروید هنگام درخواست سایت
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                // تلاش برای استفاده از Intent خود مرورگر و در غیر این صورت باز کردن کلی فایل‌ها
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                return try {
                    startActivityForResult(Intent.createChooser(intent, "انتخاب مدل ۳بعدی"), FILE_CHOOSER_REQUEST_CODE)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(applicationContext, "امکان باز کردن مدیریت فایل وجود ندارد", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        webView.loadUrl("https://www.mixamo.com")
    }

    // ارسال فایل انتخاب‌شده از حافظه گوشی به مرورگر
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        
        // مجوزهای دسترسی کامل به فایل
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // اجازه باز کردن پنجره‌ها و دیالوگ‌های فایل
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)

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

                function fireMouseEvent(type, target, touch) {
                    const evt = new MouseEvent(type, {
                        bubbles: true, cancelable: true, view: window,
                        clientX: touch.clientX, clientY: touch.clientY, button: 0
                    });
                    target.dispatchEvent(evt);
                }

                // ۱. مدیریت اختصاصی لمس دایره‌های نشانه‌گذاری (Markers)
                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length > 1) return; 
                    
                    const touch = e.touches[0];
                    const el = document.elementFromPoint(touch.clientX, touch.clientY);
                    
                    const isRiggingElement = el && el.closest('[class*="marker"], [class*="ring"], [class*="circle"], [class*="joint"], [class*="autorig"]');

                    if (isRiggingElement) {
                        e.preventDefault(); 
                        activeTarget = isRiggingElement;
                        fireMouseEvent('mousedown', activeTarget, touch);
                    }
                }, { passive: false });

                window.addEventListener('touchmove', function(e) {
                    if (!activeTarget) return;
                    e.preventDefault();
                    const touch = e.touches[0];
                    fireMouseEvent('mousemove', activeTarget, touch);
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    if (activeTarget) {
                        const touch = e.changedTouches[0];
                        fireMouseEvent('mouseup', activeTarget, touch);
                        activeTarget = null;
                        return;
                    }

                    // ۲. حل مشکل دکمه Select Character File در پنجره آپلود
                    const touch = e.changedTouches[0];
                    const el = document.elementFromPoint(touch.clientX, touch.clientY);
                    if (el) {
                        // اگر روی کادر آپلود یا متن Select Character File تاچ شد
                        const isSelectFile = el.innerText && el.innerText.includes('Select Character File');
                        const fileInput = document.querySelector('input[type="file"]');

                        if (isSelectFile || el.closest('[class*="upload"], [class*="drop"]')) {
                            if (fileInput) {
                                fileInput.click(); // تحریک مستقیم فایل‌اینپوت مخفی میکسیمو
                            }
                        }
                    }
                }, { passive: false });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }
}
