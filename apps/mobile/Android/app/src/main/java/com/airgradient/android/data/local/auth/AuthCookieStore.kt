package com.airgradient.android.data.local.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AuthCookieStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun saveFromResponse(cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val persisted = readPersistedCookies().toMutableList()
            val now = System.currentTimeMillis()
            persisted.removeAll { stored -> stored.isExpired(now) }
            cookies.forEach { cookie ->
                if (cookie.isExpired(now)) return@forEach
                persisted.removeAll { stored -> stored.matches(cookie) }
                persisted += PersistedCookie.from(cookie)
            }
            writePersistedCookies(persisted)
        }
    }

    fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val persisted = readPersistedCookies().toMutableList()
            val valid = persisted.filterNot { it.isExpired(now) }
            if (valid.size != persisted.size) {
                writePersistedCookies(valid)
            }
            return valid
                .mapNotNull { it.toOkHttpCookie() }
                .filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(lock) {
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    fun hasValidCookies(): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val persisted = readPersistedCookies()
            val hasValid = persisted.any { !it.isExpired(now) }
            if (!hasValid && persisted.isNotEmpty()) {
                prefs.edit().remove(KEY_COOKIES).apply()
            }
            return hasValid
        }
    }

    private fun readPersistedCookies(): List<PersistedCookie> {
        val stored = prefs.getString(KEY_COOKIES, null) ?: return emptyList()
        val array = JSONArray(stored)
        val result = mutableListOf<PersistedCookie>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            PersistedCookie.from(json)?.let { result += it }
        }
        return result
    }

    private fun writePersistedCookies(cookies: List<PersistedCookie>) {
        val array = JSONArray()
        cookies.forEach { cookie ->
            array.put(cookie.toJson())
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    private fun Cookie.matches(url: HttpUrl): Boolean {
        val domainMatch = if (hostOnly) {
            url.host == domain
        } else {
            url.host == domain || url.host.endsWith(".$domain")
        }
        val pathMatch = url.encodedPath.startsWith(path)
        val secureMatch = !secure || url.isHttps
        return domainMatch && pathMatch && secureMatch
    }

    private fun Cookie.isExpired(now: Long): Boolean = expiresAt < now

    private data class PersistedCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    ) {
        fun toOkHttpCookie(): Cookie? {
            return try {
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)
                    .expiresAt(expiresAt)

                if (hostOnly) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (secure) builder.secure()
                if (httpOnly) builder.httpOnly()
                builder.build()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        fun isExpired(now: Long): Boolean = expiresAt < now

        fun matches(cookie: Cookie): Boolean {
            return name == cookie.name && domain == cookie.domain && path == cookie.path
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("name", name)
                put("value", value)
                put("domain", domain)
                put("path", path)
                put("expiresAt", expiresAt)
                put("secure", secure)
                put("httpOnly", httpOnly)
                put(
                    "hostOnly",
                    hostOnly
                )
            }
        }

        companion object {
            fun from(cookie: Cookie): PersistedCookie {
                return PersistedCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly
                )
            }

            fun from(json: JSONObject): PersistedCookie? {
                return try {
                    PersistedCookie(
                        name = json.getString("name"),
                        value = json.getString("value"),
                        domain = json.getString("domain"),
                        path = json.getString("path"),
                        expiresAt = json.getLong("expiresAt"),
                        secure = json.optBoolean("secure", false),
                        httpOnly = json.optBoolean("httpOnly", false),
                        hostOnly = json.optBoolean("hostOnly", false)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "auth_cookie_store"
        private const val KEY_COOKIES = "cookies"
    }
}
