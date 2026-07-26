package com.vlesshub.vpn.core

import com.vlesshub.vpn.model.ServerProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Собирает JSON-конфиг для Xray-core (формат такой же, как в CLI xray -config).
 * Локальный inbound — SOCKS/HTTP на localhost, который tun2socks заворачивает
 * в системный VPN-туннель (см. VlessHubVpnService).
 */
object XrayConfigBuilder {

    private const val LOCAL_SOCKS_PORT = 10808
    private const val LOCAL_HTTP_PORT = 10809

    fun build(profile: ServerProfile): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // Локальные inbound-ы, в которые tun2socks шлёт трафик из VPN-туннеля
        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("tag", "socks-in")
                .put("port", LOCAL_SOCKS_PORT)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
        )
        inbounds.put(
            JSONObject()
                .put("tag", "http-in")
                .put("port", LOCAL_HTTP_PORT)
                .put("listen", "127.0.0.1")
                .put("protocol", "http")
        )
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(profile))
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        root.put("outbounds", outbounds)

        return root.toString(2)
    }

    private fun buildProxyOutbound(p: ServerProfile): JSONObject {
        val outbound = JSONObject().put("tag", "proxy")
        val streamSettings = buildStreamSettings(p)

        when (p.protocol) {
            ServerProfile.Protocol.VLESS -> {
                outbound.put("protocol", "vless")
                val user = JSONObject().apply {
                    put("id", p.uuidOrPassword)
                    put("encryption", "none")
                    p.flow?.let { put("flow", it) }
                }
                val server = JSONObject().apply {
                    put("address", p.address)
                    put("port", p.port)
                    put("users", JSONArray().put(user))
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            }
            ServerProfile.Protocol.VMESS -> {
                outbound.put("protocol", "vmess")
                val user = JSONObject().apply {
                    put("id", p.uuidOrPassword)
                    put("alterId", p.alterId)
                    put("security", p.vmessSecurity)
                }
                val server = JSONObject().apply {
                    put("address", p.address)
                    put("port", p.port)
                    put("users", JSONArray().put(user))
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            }
            ServerProfile.Protocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                val server = JSONObject().apply {
                    put("address", p.address)
                    put("port", p.port)
                    put("password", p.uuidOrPassword)
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(server)))
            }
            ServerProfile.Protocol.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                val (method, password) = p.uuidOrPassword.split(":", limit = 2)
                val server = JSONObject().apply {
                    put("address", p.address)
                    put("port", p.port)
                    put("method", method)
                    put("password", password)
                }
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(server)))
            }
        }

        outbound.put("streamSettings", streamSettings)
        return outbound
    }

    private fun buildStreamSettings(p: ServerProfile): JSONObject {
        val stream = JSONObject().put("network", p.network)

        when (p.security) {
            "tls" -> {
                stream.put("security", "tls")
                val tls = JSONObject()
                p.sni?.let { tls.put("serverName", it) }
                p.alpn?.let { tls.put("alpn", JSONArray(it.split(","))) }
                p.fingerprint?.let { tls.put("fingerprint", it) }
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                stream.put("security", "reality")
                val reality = JSONObject()
                p.sni?.let { reality.put("serverName", it) }
                p.fingerprint?.let { reality.put("fingerprint", it) }
                p.publicKey?.let { reality.put("publicKey", it) }
                p.shortId?.let { reality.put("shortId", it) }
                p.spiderX?.let { reality.put("spiderX", it) }
                stream.put("realitySettings", reality)
            }
        }

        when (p.network) {
            "ws" -> {
                val ws = JSONObject()
                p.path?.let { ws.put("path", it) }
                p.host?.let { ws.put("headers", JSONObject().put("Host", it)) }
                stream.put("wsSettings", ws)
            }
            "grpc" -> {
                val grpc = JSONObject()
                p.serviceName?.let { grpc.put("serviceName", it) }
                stream.put("grpcSettings", grpc)
            }
            "http" -> {
                val http = JSONObject()
                p.path?.let { http.put("path", it) }
                p.host?.let { http.put("host", JSONArray().put(it)) }
                stream.put("httpSettings", http)
            }
        }

        return stream
    }

    fun localSocksPort() = LOCAL_SOCKS_PORT
}
