package com.vlesshub.vpn.util

import android.util.Base64
import com.vlesshub.vpn.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/**
 * Обрабатывает ссылки вида:
 *   happ://add/https://xray.abvpn.ru/vless/<uuid>/<id>.json#abvpn
 *
 * Формат: happ://add/<целевой URL>[#заголовок профиля].
 * Целевой URL отдаёт JSON, который может быть в одном из форматов:
 *  1) один outbound Xray ("protocol" + "settings" + "streamSettings" в корне)
 *  2) полный конфиг Xray со списком "outbounds" — берём первый рабочий прокси-outbound
 *  3) обычная subscription-строка/список ссылок vless:// vmess:// trojan:// ss://
 *     (в т.ч. base64-закодированная, построчно)
 *  4) массив JSON-объектов профилей (см. п.1) — импортируем все как список
 */
object SubscriptionImporter {

    /**
     * @return список профилей (обычно один, но happ-json может отдавать и несколько)
     */
    suspend fun importFromHappLink(happLink: String): List<ServerProfile> {
        val (targetUrl, titleOverride) = splitHappLink(happLink)
            ?: throw IllegalArgumentException("Некорректная ссылка happ://add/...")

        val raw = fetch(targetUrl)
        val profiles = parseSubscriptionBody(raw)

        if (profiles.isEmpty()) {
            throw IllegalStateException("Сервер не вернул ни одного профиля")
        }

        // Заголовок из фрагмента ссылки (#abvpn) применяем, только если он есть
        // и в самом JSON не пришло собственное осмысленное имя (когда профиль один).
        return if (titleOverride != null && profiles.size == 1) {
            listOf(profiles[0].copy(remark = titleOverride))
        } else {
            profiles
        }
    }

    fun isHappAddLink(raw: String): Boolean = raw.trim().startsWith("happ://add/")

    private fun splitHappLink(raw: String): Pair<String, String?>? {
        val body = raw.trim().removePrefix("happ://add/")
        if (body.isBlank()) return null
        val hashIndex = body.lastIndexOf('#')
        return if (hashIndex >= 0) {
            val url = body.substring(0, hashIndex)
            val title = runCatching {
                java.net.URLDecoder.decode(body.substring(hashIndex + 1), "UTF-8")
            }.getOrNull()
            url to title
        } else {
            body to null
        }
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "VlessHub/1.0")
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSubscriptionBody(body: String): List<ServerProfile> {
        val trimmed = body.trim()

        // Случай 3б: возможно это base64 без JSON-структуры вовсе
        val asJsonCandidate = runCatching { JSONTokenerPeek(trimmed) }.getOrNull()

        if (asJsonCandidate == null) {
            // Не похоже на JSON — пробуем как обычную subscription (список ссылок,
            // возможно закодированную в base64, по одной ссылке на строку)
            return parsePlainSubscription(trimmed)
        }

        return when (asJsonCandidate) {
            '{' -> {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("outbounds") -> parseFullXrayConfig(obj)
                    obj.has("protocol") -> listOfNotNull(parseSingleOutboundJson(obj))
                    else -> emptyList()
                }
            }
            '[' -> {
                val arr = JSONArray(trimmed)
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.get(i)
                    when (item) {
                        is JSONObject -> parseSingleOutboundJson(item)
                        is String -> ConfigParser.parse(item)
                        else -> null
                    }
                }
            }
            else -> emptyList()
        }
    }

    /** Возвращает первый символ непустого JSON-тела ('{' или '[') либо null, если это не JSON. */
    private fun JSONTokenerPeek(s: String): Char? {
        val c = s.firstOrNull() ?: return null
        return if (c == '{' || c == '[') c else null
    }

    private fun parsePlainSubscription(trimmed: String): List<ServerProfile> {
        // Пробуем как есть (список ссылок построчно)
        val direct = trimmed.lines().mapNotNull { ConfigParser.parse(it.trim()) }
        if (direct.isNotEmpty()) return direct

        // Пробуем декодировать как base64 (классический формат subscription)
        return runCatching {
            val decoded = String(Base64.decode(trimmed, Base64.DEFAULT))
            decoded.lines().mapNotNull { ConfigParser.parse(it.trim()) }
        }.getOrDefault(emptyList())
    }

    private fun parseFullXrayConfig(root: JSONObject): List<ServerProfile> {
        val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
        val results = mutableListOf<ServerProfile>()
        for (i in 0 until outbounds.length()) {
            val ob = outbounds.getJSONObject(i)
            val protocol = ob.optString("protocol")
            if (protocol in setOf("freedom", "blackhole", "dns")) continue
            parseSingleOutboundJson(ob)?.let { results.add(it) }
        }
        return results
    }

    /**
     * Разбирает один outbound-объект Xray (protocol/settings/streamSettings) — обратная
     * операция к XrayConfigBuilder.buildProxyOutbound(). Используется для happ-json конфигов,
     * которые присылают уже готовый outbound вместо ссылки vless://...
     */
    private fun parseSingleOutboundJson(ob: JSONObject): ServerProfile? = runCatching {
        val protocolStr = ob.optString("protocol").lowercase()
        val settings = ob.optJSONObject("settings") ?: JSONObject()
        val streamSettings = ob.optJSONObject("streamSettings") ?: JSONObject()
        val remark = ob.optString("tag").ifBlank { ob.optString("ps") }.ifBlank { protocolStr }

        val protocol = when (protocolStr) {
            "vless" -> ServerProfile.Protocol.VLESS
            "vmess" -> ServerProfile.Protocol.VMESS
            "trojan" -> ServerProfile.Protocol.TROJAN
            "shadowsocks" -> ServerProfile.Protocol.SHADOWSOCKS
            else -> return null
        }

        var address = ""
        var port = 443
        var uuidOrPassword = ""
        var flow: String? = null
        var alterId = 0
        var vmessSecurity = "auto"

        when (protocol) {
            ServerProfile.Protocol.VLESS, ServerProfile.Protocol.VMESS -> {
                val vnext = settings.optJSONArray("vnext")?.optJSONObject(0) ?: JSONObject()
                address = vnext.optString("address")
                port = vnext.optInt("port", 443)
                val user = vnext.optJSONArray("users")?.optJSONObject(0) ?: JSONObject()
                uuidOrPassword = user.optString("id")
                flow = user.optString("flow").ifBlank { null }
                alterId = user.optInt("alterId", 0)
                vmessSecurity = user.optString("security", "auto")
            }
            ServerProfile.Protocol.TROJAN -> {
                val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: JSONObject()
                address = server.optString("address")
                port = server.optInt("port", 443)
                uuidOrPassword = server.optString("password")
            }
            ServerProfile.Protocol.SHADOWSOCKS -> {
                val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: JSONObject()
                address = server.optString("address")
                port = server.optInt("port", 443)
                uuidOrPassword = "${server.optString("method")}:${server.optString("password")}"
            }
        }

        if (address.isBlank() || uuidOrPassword.isBlank()) return null

        val network = streamSettings.optString("network", "tcp")
        val security = streamSettings.optString("security", "none")

        var sni: String? = null
        var alpn: String? = null
        var fingerprint: String? = null
        var publicKey: String? = null
        var shortId: String? = null
        var spiderX: String? = null

        streamSettings.optJSONObject("tlsSettings")?.let {
            sni = it.optString("serverName").ifBlank { null }
            fingerprint = it.optString("fingerprint").ifBlank { null }
            it.optJSONArray("alpn")?.let { arr ->
                alpn = (0 until arr.length()).joinToString(",") { i -> arr.getString(i) }
            }
        }
        streamSettings.optJSONObject("realitySettings")?.let {
            sni = it.optString("serverName").ifBlank { null }
            fingerprint = it.optString("fingerprint").ifBlank { null }
            publicKey = it.optString("publicKey").ifBlank { null }
            shortId = it.optString("shortId").ifBlank { null }
            spiderX = it.optString("spiderX").ifBlank { null }
        }

        var path: String? = null
        var host: String? = null
        var serviceName: String? = null
        when (network) {
            "ws" -> streamSettings.optJSONObject("wsSettings")?.let {
                path = it.optString("path").ifBlank { null }
                host = it.optJSONObject("headers")?.optString("Host")?.ifBlank { null }
            }
            "grpc" -> streamSettings.optJSONObject("grpcSettings")?.let {
                serviceName = it.optString("serviceName").ifBlank { null }
            }
            "http" -> streamSettings.optJSONObject("httpSettings")?.let {
                path = it.optString("path").ifBlank { null }
                host = it.optJSONArray("host")?.optString(0)?.ifBlank { null }
            }
        }

        ServerProfile(
            id = UUID.randomUUID().toString(),
            remark = remark,
            protocol = protocol,
            address = address,
            port = port,
            uuidOrPassword = uuidOrPassword,
            network = network,
            security = security,
            sni = sni,
            alpn = alpn,
            fingerprint = fingerprint,
            flow = flow,
            publicKey = publicKey,
            shortId = shortId,
            spiderX = spiderX,
            path = path,
            host = host,
            serviceName = serviceName,
            alterId = alterId,
            vmessSecurity = vmessSecurity
        )
    }.getOrNull()
}
