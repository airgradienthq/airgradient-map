package com.airgradient.android.data.local.auth

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

@Singleton
class AuthCookieJar @Inject constructor(
    private val store: AuthCookieStore
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.saveFromResponse(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store.loadForRequest(url)
    }

    fun clear() {
        store.clear()
    }

    fun hasValidCookies(): Boolean = store.hasValidCookies()
}
