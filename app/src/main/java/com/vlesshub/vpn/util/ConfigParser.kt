package com.vlesshub.vpn.util

import android.util.Base64
import com.vlesshub.vpn.model.ServerProfile
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * Парсит ссылки-конфиги в универсальный ServerProfile.
 * Поддерживает форматы, которые используют актуальные генераторы
 * (3x-ui, Marzban, x-ui и т.п.) для vless/vmess/trojan/ss.
 */
object ConfigParser {

    fun parse(raw: String): ServerProfile? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
            else -> null
        }
    }

    private fun parseVless(raw: String): ServerProfile? = runCatching {
        val uri = URI(raw)
        val params = queryParams(uri.rawQuery)
        ServerProfile(
            id = UUID.randomUUID().toString(),
            remark = decodeFragment(uri.rawFragment) ?: "VLESS ${uri.host}",
            protocol = ServerProfile.Protocol.VLESS,
            address = uri.host ?: return null,
            port = if (uri.port != -1) uri.port else 443,
            uuidOrPassword = uri.userInfo ?: return null,
            network = params["type"] ?: "tcp",
            security = params["security"] ?: "none",
            sni = params["sni"],
            alpn = params["alpn"],
            fingerprint = params["fp"],
            flow = params["flow"],
            publicKey = params["pbk"],
            shortId = params["sid"],
            spiderX = params["spx"],
            path = params["path"],
            host = params["host"],
            serviceName = params["serviceName"]
        )
    }.getOrNull()

    private fun parseTrojan(raw: String): ServerProfile? = runCatching {
        val uri = URI(raw)
        val params = queryParams(uri.rawQuery)
        ServerProfile(
            id = UUID.randomUUID().toString(),
            remark = decodeFragment(uri.rawFragment) ?: "Trojan ${uri.host}",
            protocol = ServerProfile.Protocol.TROJAN,
            address = uri.host ?: return null,
            port = if (uri.port != -1) uri.port else 443,
            uuidOrPassword = uri.userInfo ?: return null,
            network = params["type"] ?: "tcp",
            security = params["security"] ?: "tls",
            sni = params["sni"],
            alpn = params["alpn"],
            fingerprint = params["fp"],
            path = params["path"],
            host = params["host"]
        )
    }.getOrNull()

    private fun parseVmess(raw: String): ServerProfile? = runCatching {
        val b64 = raw.removePrefix("vmess://")
        val json = JSONObject(String(Base64.decode(b64, Base64.DEFAULT)))
        ServerProfile(
            id = UUID.randomUUID().toString(),
            remark = json.optString("ps", "VMess"),
            protocol = ServerProfile.Protocol.VMESS,
            address = json.getString("add"),
            port = json.getString("port").toInt(),
            uuidOrPassword = json.getString("id"),
            network = json.optString("net", "tcp"),
            security = json.optString("tls", "none"),
            sni = json.optString("sni").ifBlank { null },
            path = json.optString("path").ifBlank { null },
            host = json.optString("host").ifBlank { null },
            alterId = json.optString("aid", "0").toIntOrNull() ?: 0,
            vmessSecurity = json.optString("scy", "auto")
        )
    }.getOrNull()

    private fun parseShadowsocks(raw: String): ServerProfile? = runCatching {
        // ss://base64(method:password)@host:port#remark  ИЛИ  ss://base64(全部)
        val withoutScheme = raw.removePrefix("ss://")
        val fragmentSplit = withoutScheme.split("#", limit = 2)
        val body = fragmentSplit[0]
        val remark = fragmentSplit.getOrNull(1)?.let { decodeFragment(it) } ?: "Shadowsocks"

        val (userInfoPart, hostPart) = if (body.contains("@")) {
            body.split("@", limit = 2)
        } else {
            // Полностью закодированный вариант
            val decoded = String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            decoded.split("@", limit = 2)
        }

        val methodPass = String(Base64.decode(userInfoPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val (method, password) = methodPass.split(":", limit = 2)
        val (host, port) = hostPart.split(":", limit = 2)

        ServerProfile(
            id = UUID.randomUUID().toString(),
            remark = remark,
            protocol = ServerProfile.Protocol.SHADOWSOCKS,
            address = host,
            port = port.toInt(),
            uuidOrPassword = "$method:$password"
        )
    }.getOrNull()

    private fun queryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
    }

    private fun decodeFragment(fragment: String?): String? =
        fragment?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
}
