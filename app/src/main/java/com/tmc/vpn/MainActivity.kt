package com.tmc.vpn

import android.content.Context
import android.content.Intent
import android.net.Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmc.vpn.servers.ServerCatalog
import com.tmc.vpn.servers.ServerKind
import com.tmc.vpn.servers.SecureProfileStore
import com.tmc.vpn.vpn.TrafficSnapshot
import com.tmc.vpn.vpn.TunnelStatus
import com.tmc.vpn.vpn.WireGuardController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF081426)
private val Panel = Color(0xFF162943)
private val Cyan = Color(0xFF21D9F5)
private val Green = Color(0xFF4CE4B2)
private val Red = Color(0xFFF04A64)
private val pages = listOf("Inicio", "Servidores", "Diagnóstico", "Estadísticas", "Seguridad", "Ajustes", "Cuenta")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = WireGuardController(this)
        setContent { UltimateApp(this, controller) }
    }
}

@Composable
private fun UltimateApp(context: Context, controller: WireGuardController) {
    var page by remember { mutableIntStateOf(0) }
    var welcome by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var hasProfile by remember { mutableStateOf(false) }
    val profileStore = remember { SecureProfileStore(context) }
    val catalog = remember { ServerCatalog(context) }
    var catalogVersion by remember { mutableIntStateOf(0) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }
    var tunnelStatus by remember { mutableStateOf(TunnelStatus.DISCONNECTED) }
    var message by remember { mutableStateOf("Sin perfil WireGuard") }
    var busy by remember { mutableStateOf(false) }
    var notificationPreference by remember { mutableStateOf(true) }
    val recoveryPreferences = remember { context.getSharedPreferences("tmc_vpn_recovery", Context.MODE_PRIVATE) }
    var autoRecovery by remember { mutableStateOf(recoveryPreferences.getBoolean("enabled", false)) }
    var recoveryEvents by remember { mutableIntStateOf(0) }
    var manualDisconnect by remember { mutableStateOf(false) }
    var lastRecoveryMessage by remember { mutableStateOf("Sin recuperaciones solicitadas") }
    var connectionHistory by remember { mutableStateOf(listOf<String>()) }
    fun recordEvent(event: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        connectionHistory = (listOf("$timestamp • $event") + connectionHistory).take(20)
    }
    var traffic by remember { mutableStateOf<TrafficSnapshot?>(null) }
    var trafficError by remember { mutableStateOf("Conecta el túnel para consultar los contadores.") }
    val scope = rememberCoroutineScope()
    val network = remember(refresh, tunnelStatus) { networkType(context, tunnelStatus) }
    val diagnostics = remember(refresh, tunnelStatus) { networkDiagnostics(context) }
    var pendingConnect by remember { mutableStateOf(false) }
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) pendingConnect = true
        else message = "Permiso VPN rechazado"
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("No se pudo leer el archivo")
                check(tunnelStatus == TunnelStatus.DISCONNECTED && !busy) { "Espera a que finalice la operación VPN antes de importar" }
                val imported = catalog.add("Perfil importado " + (catalog.list().size + 1))
                try { profileStore.save(imported.id, text); controller.importProfile(profileStore.load(imported.id)).getOrThrow() }
                catch (e: Exception) { profileStore.delete(imported.id); catalog.remove(imported.id); throw e }
                selectedServerId = imported.id
            }.onSuccess {
                hasProfile = true
                catalogVersion++
                tunnelStatus = TunnelStatus.DISCONNECTED
                message = "Perfil cifrado asociado al servidor seleccionado."
            }.onFailure { message = "Error al importar: ${it.localizedMessage ?: "perfil inválido"}" }
        }
    }
    fun toggleConnection() {
        if (busy || !hasProfile) return
        if (tunnelStatus == TunnelStatus.CONNECTING) { message = "Espera a que termine la conexión"; return }
        if (tunnelStatus != TunnelStatus.CONNECTED) {
            val permission = VpnService.prepare(context)
            if (permission != null) { vpnPermission.launch(permission); return }
        }
        val disconnectRequested = tunnelStatus == TunnelStatus.CONNECTED
        if (disconnectRequested) {
            manualDisconnect = true
            recoveryEvents = 0
        }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (disconnectRequested) controller.disconnect() else controller.connect()
            }
            if (!disconnectRequested && result.isSuccess && controller.status == TunnelStatus.CONNECTED) manualDisconnect = false
            tunnelStatus = controller.status
            message = result.fold(
                onSuccess = { if (tunnelStatus == TunnelStatus.CONNECTED) "Túnel WireGuard activado" else "Túnel desconectado" },
                onFailure = { "Error VPN: ${it.localizedMessage ?: "desconocido"}" }
            )
            recordEvent(if (result.isSuccess) { if (disconnectRequested) "Desconexión manual" else "Conexión activada" } else { if (disconnectRequested) "Error al desconectar" else "Error al conectar" })
            busy = false
        }
    }
    // Monitor only while this activity is visible. Do not reconnect after a manual disconnect.
    DisposableEffect(context, autoRecovery) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var initialEvent = true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (initialEvent) { initialEvent = false; return }
                scope.launch {
                    refresh++
                    if (autoRecovery && !manualDisconnect && tunnelStatus == TunnelStatus.CONNECTED && !busy) recoveryEvents++
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    LaunchedEffect(recoveryEvents) {
        if (recoveryEvents == 0 || !autoRecovery || manualDisconnect || tunnelStatus != TunnelStatus.CONNECTED || busy) return@LaunchedEffect
        delay(2500)
        if (!autoRecovery || manualDisconnect || tunnelStatus != TunnelStatus.CONNECTED || busy) return@LaunchedEffect
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) {
            lastRecoveryMessage = "Cambio de red detectado; esperando conectividad validada"
            return@LaunchedEffect
        }
        busy = true
        val result = withContext(Dispatchers.IO) { controller.connect() }
        tunnelStatus = controller.status
        recordEvent(if (result.isSuccess) "Reconexión solicitada tras cambio de red" else "Error de reconexión")
        lastRecoveryMessage = result.fold(
            onSuccess = { "Reactivación solicitada al cambiar de red" },
            onFailure = { "No se pudo reactivar: ${it.localizedMessage ?: "error"}" }
        )
        busy = false
    }
    LaunchedEffect(page, tunnelStatus) {
        traffic = null
        if (page != 3 || tunnelStatus != TunnelStatus.CONNECTED) return@LaunchedEffect
        while (true) {
            val result = withContext(Dispatchers.IO) { controller.trafficSnapshot() }
            result.onSuccess { traffic = it; trafficError = "" }
                .onFailure { traffic = null; trafficError = it.localizedMessage ?: "Estadísticas no disponibles" }
            delay(3000)
        }
    }
    LaunchedEffect(pendingConnect) {
        if (pendingConnect) { pendingConnect = false; toggleConnection() }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Navy, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Navy).padding(16.dp)) {
            if (welcome) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.tmc_vpn_logo_3d), contentDescription = "TMC VPN", modifier = Modifier.size(166.dp))
                    Text("TMC VPN ULTIMATE", fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text("WireGuard • Conexión privada", color = Cyan)
                }
                Spacer(Modifier.height(28.dp))
                Text("Bienvenido. Importa un perfil WireGuard autorizado. Una VPN requiere conexión a Internet y no genera datos móviles gratuitos.", textAlign = TextAlign.Center, color = Color.LightGray)
                Spacer(Modifier.weight(1f))
                Button(onClick = { welcome = false }, modifier = Modifier.fillMaxWidth()) { Text("Comenzar") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.tmc_vpn_logo_3d), contentDescription = "Logo", modifier = Modifier.size(62.dp))
                    Column {
                        Text("TMC VPN", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("ULTIMATE 4.2 • WireGuard", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(pages[page], fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Cyan)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (page) {
                        0 -> {
                            Spacer(Modifier.height(14.dp))
                            Image(painterResource(R.drawable.tmc_vpn_logo_3d), contentDescription = null, modifier = Modifier.size(140.dp))
                            val active = tunnelStatus == TunnelStatus.CONNECTED
                            Text(when (tunnelStatus) { TunnelStatus.CONNECTED -> "TÚNEL ACTIVO"; TunnelStatus.CONNECTING -> "CONECTANDO…"; TunnelStatus.ERROR -> "ERROR DE CONEXIÓN"; else -> "DESCONECTADO" }, color = if (active) Green else Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(message, textAlign = TextAlign.Center, color = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { toggleConnection() }, enabled = hasProfile && !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (busy) "Procesando…" else if (active) "Desconectar" else "Conectar WireGuard")
                            }
                            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy) { Text("Importar perfil .conf") }
                            Info("Red", network)
                            Info("Estado", if (active) "WireGuard reporta túnel UP. Comprueba conectividad y DNS." else "No hay túnel activo.")
                            Info("Recuperación de red", lastRecoveryMessage)
                        }
                        1 -> {
                            Text("🇩🇴 INTERNET REPÚBLICA DOMINICANA", color = Cyan, fontWeight = FontWeight.Bold)
                            Info("Servidores gratuitos", "Sin servidores públicos verificados. Agrega solamente perfiles WireGuard autorizados.")
                            val servers = remember(catalogVersion) { catalog.list().sortedWith(compareByDescending<com.tmc.vpn.servers.VpnServer> { it.favorite }.thenBy { it.name }) }
                            val suggested = servers.firstOrNull { it.favorite } ?: servers.firstOrNull()
                            if (suggested != null) {
                                Info("Sugerencia local", suggested.name + if (suggested.favorite) " • Favorito" else " • Primer perfil disponible")
                                Info("Criterio", "Ordena perfiles guardados priorizando favoritos. No mide latencia, disponibilidad ni velocidad. Selecciona manualmente antes de conectar.")
                                Button(onClick = {
                                    if (tunnelStatus != TunnelStatus.DISCONNECTED || busy) {
                                        message = "Desconecta el túnel antes de seleccionar otro servidor"
                                    } else {
                                        runCatching { controller.importProfile(profileStore.load(suggested.id)).getOrThrow() }
                                            .onSuccess { selectedServerId = suggested.id; hasProfile = true; message = "Perfil sugerido seleccionado: " + suggested.name }
                                            .onFailure { message = "No se pudo cargar el perfil sugerido: ${it.localizedMessage ?: "error"}" }
                                    }
                                }, enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy) { Text("Seleccionar sugerido") }
                            }
                            if (servers.isEmpty()) Info("Catálogo vacío", "Importa tu primer perfil .conf para comenzar.")
                            servers.forEach { server ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Panel, RoundedCornerShape(14.dp)).padding(12.dp)) {
                                    Text(server.name + if (selectedServerId == server.id) " • Seleccionado" else "", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(if (server.kind == ServerKind.FREE) "Gratuito verificado" else "Personalizado", color = Color.LightGray)
                                    Text("Latencia: sin medir", color = Color.LightGray, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            if (tunnelStatus != TunnelStatus.DISCONNECTED || busy) {
                                                message = "Espera a que finalice la operación VPN antes de seleccionar otro servidor"
                                            } else {
                                            runCatching { controller.importProfile(profileStore.load(server.id)).getOrThrow() }
                                                .onSuccess { selectedServerId = server.id; hasProfile = true; message = "Perfil seleccionado: " + server.name }
                                                .onFailure { selectedServerId = null; hasProfile = false; message = "No se pudo cargar el perfil" }
                                            }
                                        }) { Text("Seleccionar") }
                                        TextButton(onClick = { catalog.toggleFavorite(server.id); catalogVersion++ }) { Text(if (server.favorite) "★" else "☆") }
                                        TextButton(onClick = { if (tunnelStatus == TunnelStatus.DISCONNECTED && !busy) { profileStore.delete(server.id); catalog.remove(server.id); catalogVersion++; if (selectedServerId == server.id) { selectedServerId = null; hasProfile = false } } else message = "Desconecta antes de eliminar un servidor" }) { Text("Eliminar") }
                                    }
                                }
                            }
                            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy) { Text("Importar configuración WireGuard") }
                            Info("Seguridad", "Perfiles cifrados con Android Keystore; favoritos guardan metadatos.")
                        }
                        2 -> {
                            Info("Tipo de red", network)
                            Info("Validación de Internet", diagnostics.internet)
                            Info("DNS configurados", diagnostics.dns)
                            Info("Ruta de conexión", diagnostics.route)
                            Info("Estado WireGuard", when (tunnelStatus) { TunnelStatus.CONNECTED -> "Conectado"; TunnelStatus.CONNECTING -> "Conectando"; TunnelStatus.ERROR -> "Error; revisa la pantalla Inicio"; else -> "Desconectado" })
                            Button(onClick = { refresh++ }) { Text("Actualizar diagnóstico") }
                            Info("Nota", "La validación del sistema no garantiza ausencia de fugas DNS. La latencia real requiere una prueba activa.")
                            Info("Historial de esta sesión", if (connectionHistory.isEmpty()) "Todavía no hay eventos de conexión." else connectionHistory.joinToString("\\n"))
                            Button(onClick = { connectionHistory = emptyList() }, enabled = connectionHistory.isNotEmpty()) { Text("Limpiar historial") }
                            Info("Privacidad del historial", "Solo en memoria durante esta sesión; no almacena claves, perfiles ni direcciones IP.")
                        }
                        3 -> {
                            Info("Estado del túnel", tunnelStatus.name)
                            Info("Recibido (WireGuard)", traffic?.let { formatTraffic(it.receivedBytes) } ?: "No disponible")
                            Info("Enviado (WireGuard)", traffic?.let { formatTraffic(it.sentBytes) } ?: "No disponible")
                            if (traffic == null) Info("Estado de lectura", trafficError)
                            Info("Medición", "Contadores del túnel actual, actualizados cada 3 segundos mientras ves esta pantalla. No son el consumo total de datos móviles.")
                        }
                        4 -> {
                            Info("WireGuard", if (hasProfile) "Perfil válido importado en memoria" else "Sin perfil")
                            Info("VPN siempre activa", "Configúrala en Ajustes de Android > VPN después de conectar correctamente.")
                            Info("Bloquear sin VPN", "Activa esta protección en Android si necesitas evitar tráfico fuera del túnel.")
                            Info("Privacidad", "Perfiles cifrados con clave no exportable de Android Keystore.")
                        }
                        5 -> {
                            Info("Versión", "4.2.0 (historial y diagnóstico)")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Recuperar túnel al cambiar de red", modifier = Modifier.weight(1f))
                                Switch(checked = autoRecovery, onCheckedChange = { enabled ->
                                    autoRecovery = enabled
                                    recoveryPreferences.edit().putBoolean("enabled", enabled).apply()
                                })
                            }
                            Info("Alcance", "La preferencia queda guardada al cerrar y abrir la aplicación. La vigilancia de cambios de red sigue siendo experimental y no es un servicio permanente en segundo plano ni un kill switch.")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Preferencia de notificaciones", modifier = Modifier.weight(1f))
                                Switch(checked = notificationPreference, onCheckedChange = { notificationPreference = it })
                            }
                            Info("Aviso", "Esta preferencia todavía no cambia los permisos de Android.")
                            Button(onClick = { welcome = true }) { Text("Pantalla de bienvenida") }
                        }
                        else -> {
                            Info("Cuenta", "Sin registro obligatorio.")
                            Info("Gratis", "Importación de un perfil WireGuard personal.")
                            Info("Pro", "Concepto futuro, sin suscripciones ni pagos.")
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF29425F))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0, 1, 2, 3).forEach { i ->
                        Text(pages[i], modifier = Modifier.clickable { page = i }.padding(8.dp), fontSize = 11.sp, color = if (page == i) Cyan else Color.LightGray)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(4, 5, 6).forEach { i ->
                        Text(pages[i], modifier = Modifier.clickable { page = i }.padding(8.dp), fontSize = 11.sp, color = if (page == i) Cyan else Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable private fun Info(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Panel, RoundedCornerShape(15.dp)).padding(16.dp)) {
        Text(title, color = Green, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Color.White, fontSize = 14.sp)
    }
}

private fun networkType(context: Context, tunnelStatus: TunnelStatus): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red activa detectada"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> if (tunnelStatus == TunnelStatus.CONNECTED) "TMC VPN (WireGuard)" else "VPN activa (origen sin verificar)"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        else -> "Otra conexión"
    }
}

private data class NetworkDiagnostics(val internet: String, val dns: String, val route: String)

private fun networkDiagnostics(context: Context): NetworkDiagnostics = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return NetworkDiagnostics("Sin red activa", "No disponibles", "Sin ruta activa")
    val caps = cm.getNetworkCapabilities(network)
    val props = cm.getLinkProperties(network)
    val internet = when {
        caps == null -> "Capacidades no disponibles"
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Android confirma conectividad validada"
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Red con capacidad de Internet, aún no validada"
        else -> "Internet no confirmado por Android"
    }
    val dns = props?.dnsServers?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.hostAddress ?: "Desconocido" }
        ?: "No informados por Android"
    val route = when {
        caps == null -> "Desconocida"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "Red VPN detectada"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi sin transporte VPN detectado"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles sin transporte VPN detectado"
        else -> "Otro transporte"
    }
    NetworkDiagnostics(internet, dns, route)
}.getOrElse { NetworkDiagnostics("Diagnóstico no disponible", "No disponibles", "No disponible") }

private fun formatTraffic(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GiB".format(java.util.Locale.US, bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.2f MiB".format(java.util.Locale.US, bytes / 1_048_576.0)
    bytes >= 1024L -> "%.2f KiB".format(java.util.Locale.US, bytes / 1024.0)
    else -> "$bytes B"
}
