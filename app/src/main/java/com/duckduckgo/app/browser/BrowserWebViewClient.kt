/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.global.exception.PasswordManagerDao
import com.duckduckgo.app.global.exception.PasswordManagerEntity
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource.*
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import kotlinx.coroutines.*
import timber.log.Timber
import java.net.URI


class BrowserWebViewClient(
    private val requestRewriter: RequestRewriter,
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val cookieManager: CookieManager,
    private val passwordManagerDao: PasswordManagerDao
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null
    private var lastPageStarted: String? = null


    /**
     * This is the new method of url overriding available from API 24 onwards
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        return shouldOverride(view, url)
    }

    /**
     * * This is the old, deprecated method of url overriding available until API 23
     */
    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(view: WebView, urlString: String): Boolean {
        val url = Uri.parse(urlString)
        return shouldOverride(view, url)
    }

    /**
     * API-agnostic implementation of deciding whether to override url or not
     */
    private fun shouldOverride(webView: WebView, url: Uri): Boolean {
        try {
            Timber.v("shouldOverride $url")

            return when (val urlType = specialUrlDetector.determineType(url)) {
                is SpecialUrlDetector.UrlType.Email -> {
                    webViewClientListener?.sendEmailRequested(urlType.emailAddress)
                    true
                }
                is SpecialUrlDetector.UrlType.Telephone -> {
                    webViewClientListener?.dialTelephoneNumberRequested(urlType.telephoneNumber)
                    true
                }
                is SpecialUrlDetector.UrlType.Sms -> {
                    webViewClientListener?.sendSmsRequested(urlType.telephoneNumber)
                    true
                }
                is SpecialUrlDetector.UrlType.IntentType -> {
                    Timber.i("Found intent type link for $urlType.url")
                    launchExternalApp(urlType)
                    true
                }
                is SpecialUrlDetector.UrlType.Unknown -> {
                    Timber.w("Unable to process link type for ${urlType.url}")
                    webView.loadUrl(webView.originalUrl)
                    false
                }
                is SpecialUrlDetector.UrlType.SearchQuery -> false
                is SpecialUrlDetector.UrlType.Web -> {
                    if (requestRewriter.shouldRewriteRequest(url)) {
                        val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(url)
                        webView.loadUrl(newUri.toString())
                        return true
                    }
                    false
                }
            }
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOULD_OVERRIDE_REQUEST)
                throw e
            }
            return false
        }
    }

    private fun launchExternalApp(urlType: SpecialUrlDetector.UrlType.IntentType) {
        webViewClientListener?.externalAppLinkClicked(urlType)
    }

    @UiThread
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        try {
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
            if (url != null && url == lastPageStarted) {
                webViewClientListener?.pageRefreshed(url)
            }
            lastPageStarted = url
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PAGE_STARTED)
                throw e
            }
        }
    }

    fun test(webView: WebView) {
        val sb = StringBuilder()
//        sb.append("document.addEventListener('DOMContentLoaded', function(event){")
        sb.append("var formxxx = document.getElementsByTagName('form')[0];")
//        sb.append("alert(forms.length);")
//        sb.append("for (var i = 0; i < forms.length; i++) {")
//        sb.append("    alert(forms[i].innerHTML);")
//        sb.append("}")
        sb.append("if(!(formxxx === undefined || formxxx === null)){")
        sb.append("    var button = formxxx.getElementsByTagName('button')[0];")
        sb.append("    formxxx.onsubmit = function() {")
//        sb.append("        e.preventDefault();")
//        sb.append("        window.MYOBJECT.processHTML();")
        sb.append("        var objPWD, objAccount;var str = '';")
        sb.append("        var inputs = document.getElementsByTagName('input');")
        sb.append("        for (var i = 0; i < inputs.length; i++) {")
        sb.append("            if (inputs[i].name.toLowerCase().includes('pass')) {objPWD = inputs[i];}")
        sb.append("            else if (inputs[i].name.toLowerCase().includes('email')) {objAccount = inputs[i];}")
        sb.append("            else if (inputs[i].name.toLowerCase().includes('user')) {objAccount = inputs[i];}")
        sb.append("            if (inputs[i].id.toLowerCase().includes('pass')) {objPWD = inputs[i];}")
        sb.append("            else if (inputs[i].id.toLowerCase().includes('email')) {objAccount = inputs[i];}")
        sb.append("            else if (inputs[i].id.toLowerCase().includes('user')) {objAccount = inputs[i];}")
        sb.append("        }")
        sb.append("        if (objAccount != null) {str += objAccount.value;}")
        sb.append("        if (objPWD != null) { str += ' , ' + objPWD.value;}")
        sb.append("        window.MYOBJECT.processHTML(objAccount.value, objPWD.value, window.location.href);")
        //sb.append("window.MYOBJECT.processHTML(document.getElementsByTagName('form')[0].innerHTML);")
        //sb.append("        alert('onsubmit end, str: ' + str);")
//        sb.append("          return true;")
        sb.append("    };")
//
        sb.append("}")
//        sb.append("});")
        webView.loadUrl("javascript:$sb")
        //webView.loadUrl("javascript: window.MYOBJECT.processHTML();")
        //webView.loadUrl("javascript:alert(123);")
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        try {
            test(webView)
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
            flushCookies()
            GlobalScope.launch {
                val test = withContext(Dispatchers.IO) {
                    return@withContext passwordManagerDao.getPassword("$url")
                }
                if (test != null) {
                    webViewClientListener?.showPrompt(test)
                }
            }
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PAGE_FINISHED)
                throw e
            }
        }
    }

    private fun flushCookies() {
        GlobalScope.launch(Dispatchers.IO) {
            cookieManager.flush()
        }
    }

    @WorkerThread
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        return runBlocking {
            try {
                val documentUrl = withContext(Dispatchers.Main) { webView.url }
                Timber.v("Intercepting resource ${request.url} on page $documentUrl")
                requestInterceptor.shouldIntercept(request, webView, documentUrl, webViewClientListener)
            } catch (e: Throwable) {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOULD_INTERCEPT_REQUEST)
                throw e
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Timber.w("onRenderProcessGone. Did it crash? ${detail?.didCrash()}")
        if (detail?.didCrash() == true) {
            offlinePixelCountDataStore.webRendererGoneCrashCount += 1
        } else {
            offlinePixelCountDataStore.webRendererGoneKilledCount += 1
        }
        return super.onRenderProcessGone(view, detail)
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        try {
            Timber.v("onReceivedHttpAuthRequest ${view?.url} $realm, $host")
            if (handler != null) {
                Timber.v("onReceivedHttpAuthRequest - useHttpAuthUsernamePassword [${handler.useHttpAuthUsernamePassword()}]")
                if (handler.useHttpAuthUsernamePassword()) {
                    val credentials = buildAuthenticationCredentials(host.orEmpty(), realm.orEmpty(), view)

                    if (credentials != null) {
                        handler.proceed(credentials[0], credentials[1])
                    } else {
                        showAuthenticationDialog(view, handler, host, realm)
                    }
                } else {
                    showAuthenticationDialog(view, handler, host, realm)
                }
            } else {
                super.onReceivedHttpAuthRequest(view, handler, host, realm)
            }
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_HTTP_AUTH_REQUEST)
                throw e
            }
        }
    }

    private fun buildAuthenticationCredentials(
        host: String,
        realm: String,
        view: WebView?
    ): Array<out String>? {
        val webViewDatabase = WebViewDatabase.getInstance(view?.context)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webViewDatabase.getHttpAuthUsernamePassword(host, realm)
        } else {
            @Suppress("DEPRECATION")
            view?.getHttpAuthUsernamePassword(host, realm)
        }
    }

    private fun showAuthenticationDialog(
        view: WebView?,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?
    ) {
        webViewClientListener?.let {
            Timber.v("showAuthenticationDialog - $host, $realm")

            val siteURL = if (view?.url != null) "${URI(view.url).scheme}://$host" else host.orEmpty()

            val request = BasicAuthenticationRequest(
                handler = handler,
                host = host.orEmpty(),
                realm = realm.orEmpty(),
                site = siteURL
            )

            it.requiresAuthentication(request)
        }
    }
}