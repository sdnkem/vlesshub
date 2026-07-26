package com.vlesshub.vpn.core

import android.content.Context
import com.vlesshub.vpn.model.ServerProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Простое персистентное хранилище профилей серверов на SharedPreferences.
 * Для реального приложения можно заменить на Room, но для прототипа
 * этого достаточно и не тянет лишних зависимостей.
 */
class ProfileStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("happ_profiles", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_profile_id"

        @Volatile private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) {
                instance ?: ProfileStore(context.applicationContext).also { instance = it }
            }
    }

    fun all(): List<ServerProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }

    fun find(id: String?): ServerProfile? = id?.let { targetId -> all().find { it.id == targetId } }

    fun add(profile: ServerProfile) {
        val list = all().toMutableList()
        list.add(profile)
        save(list)
    }

    fun addAll(profiles: List<ServerProfile>) {
        val list = all().toMutableList()
        list.addAll(profiles)
        save(list)
    }

    fun update(profile: ServerProfile) {
        val list = all().map { if (it.id == profile.id) profile else it }
        save(list)
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    fun setActive(id: String) = prefs.edit().putString(KEY_ACTIVE, id).apply()
    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    private fun save(list: List<ServerProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun toJson(p: ServerProfile) = JSONObject().apply {
        put("id", p.id); put("remark", p.remark); put("protocol", p.protocol.name)
        put("address", p.address); put("port", p.port); put("uuidOrPassword", p.uuidOrPassword)
        put("network", p.network); put("security", p.security)
        put("sni", p.sni); put("alpn", p.alpn); put("fingerprint", p.fingerprint); put("flow", p.flow)
        put("publicKey", p.publicKey); put("shortId", p.shortId); put("spiderX", p.spiderX)
        put("path", p.path); put("host", p.host); put("serviceName", p.serviceName)
        put("alterId", p.alterId); put("vmessSecurity", p.vmessSecurity)
    }

    private fun fromJson(o: JSONObject) = ServerProfile(
        id = o.getString("id"),
        remark = o.getString("remark"),
        protocol = ServerProfile.Protocol.valueOf(o.getString("protocol")),
        address = o.getString("address"),
        port = o.getInt("port"),
        uuidOrPassword = o.getString("uuidOrPassword"),
        network = o.optString("network", "tcp"),
        security = o.optString("security", "none"),
        sni = o.optString("sni").ifBlank { null },
        alpn = o.optString("alpn").ifBlank { null },
        fingerprint = o.optString("fingerprint").ifBlank { null },
        flow = o.optString("flow").ifBlank { null },
        publicKey = o.optString("publicKey").ifBlank { null },
        shortId = o.optString("shortId").ifBlank { null },
        spiderX = o.optString("spiderX").ifBlank { null },
        path = o.optString("path").ifBlank { null },
        host = o.optString("host").ifBlank { null },
        serviceName = o.optString("serviceName").ifBlank { null },
        alterId = o.optInt("alterId", 0),
        vmessSecurity = o.optString("vmessSecurity", "auto")
    )
}
