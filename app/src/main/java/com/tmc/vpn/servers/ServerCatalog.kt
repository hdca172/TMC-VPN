package com.tmc.vpn.servers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ServerKind { FREE, CUSTOM }
data class VpnServer(val id: String, val name: String, val kind: ServerKind, val country: String = "República Dominicana", val profile: String? = null, val favorite: Boolean = false)

/** No fake endpoints: free servers are displayed only after an authorized profile is supplied. */
class ServerCatalog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tmc_server_catalog", Context.MODE_PRIVATE)
    fun list(): List<VpnServer> = runCatching {
        val data = JSONArray(prefs.getString("servers", "[]"))
        (0 until data.length()).map { i ->
            val o = data.getJSONObject(i)
            VpnServer(o.getString("id"), o.getString("name"), ServerKind.valueOf(o.getString("kind")), o.optString("country", "República Dominicana"), null, o.optBoolean("favorite"))
        }
    }.getOrDefault(emptyList())

    fun add(name: String, kind: ServerKind = ServerKind.CUSTOM): VpnServer {
        require(name.isNotBlank() && name.length <= 80)
        val server = VpnServer(java.util.UUID.randomUUID().toString(), name.trim(), kind)
        save(list() + server)
        return server
    }
    fun toggleFavorite(id: String) = save(list().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    fun remove(id: String) = save(list().filterNot { it.id == id })
    private fun save(items: List<VpnServer>) {
        val arr = JSONArray()
        items.forEach { s -> arr.put(JSONObject().put("id", s.id).put("name", s.name).put("kind", s.kind.name).put("country", s.country).put("favorite", s.favorite)) }
        prefs.edit().putString("servers", arr.toString()).apply()
    }
}
