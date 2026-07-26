package com.vlesshub.vpn.model

/**
 * Единая модель профиля прокси-сервера, независимая от протокола.
 * Заполняется парсером из ссылки-конфига (vless://, vmess://, trojan://, ss://)
 * и сериализуется в JSON, который скармливается ядру Xray-core.
 */
data class ServerProfile(
    val id: String,
    var remark: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val uuidOrPassword: String,

    // Транспорт / TLS
    val network: String = "tcp",       // tcp, ws, grpc, http
    val security: String = "none",     // none, tls, reality
    val sni: String? = null,
    val alpn: String? = null,
    val fingerprint: String? = null,   // uTLS fingerprint: chrome, firefox, safari...
    val flow: String? = null,          // xtls-rprx-vision и т.п. (только vless)

    // Reality
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,

    // ws/grpc/http path
    val path: String? = null,
    val host: String? = null,
    val serviceName: String? = null,

    // vmess-specific
    val alterId: Int = 0,
    val vmessSecurity: String = "auto"
) {
    enum class Protocol { VLESS, VMESS, TROJAN, SHADOWSOCKS }
}
