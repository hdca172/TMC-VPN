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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import com.tmc.vpn.ui.components.TmcConnectionButton
import com.tmc.vpn.ui.components.TmcStatusCard
import com.tmc.vpn.ui.components.TmcServerCard
import com.tmc.vpn.ui.components.TmcTrafficCard
import com.tmc.vpn.ui.theme.TmcColors
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
    var menuExpanded by remember { mutableStateOf(false) }
    var welcome by remember { mutableStateOf(true) }
    BackHandler(enabled = !welcome && (menuExpanded || page != 0)) {
        if (menuExpanded) menuExpanded = false else page = 0
    }
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
    val historyPreferences = remember { context.getSharedPreferences("tmc_vpn_history", Context.MODE_PRIVATE) }
    var connectionHistory by remember {
        mutableStateOf(runCatching {
            val saved = org.json.JSONArray(historyPreferences.getString("events", "[]"))
            (0 until saved.length()).map { saved.getString(it) }.take(20)
        }.getOrDefault(emptyList()))
    }
    fun recordEvent(event: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val updated = (listOf("$timestamp • $event") + connectionHistory).take(20)
        connectionHistory = updated
        historyPreferences.edit().putString("events", org.json.JSONArray(updated).toString()).apply()
    }
    var traffic by remember { mutableStateOf<TrafficSnapshot?>(null) }
    var trafficError by remember { mutableStateOf("Conecta el túnel para consultar los contadores.") }
    var connectedSince by remember { mutableStateOf<Long?>(null) }
    var sessionSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tunnelStatus) {
        if (tunnelStatus == TunnelStatus.CONNECTED) {
            if (connectedSince == null) connectedSince = android.os.SystemClock.elapsedRealtime()
            while (true) {
                sessionSeconds = (android.os.SystemClock.elapsedRealtime() - (connectedSince ?: android.os.SystemClock.elapsedRealtime())) / 1000L
                delay(1000)
            }
        } else {
            connectedSince = null
            sessionSeconds = 0L
        }
    }
    val scope = rememberCoroutineScope()
    val network = remember(refresh, tunnelStatus) { networkType(context, tunnelStatus) }
    val diagnostics = remember(page, refresh, tunnelStatus) {
        if (page == 2) networkDiagnostics(context) else null
    }
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
        if (tunnelStatus != TunnelStatus.CONNECTED) {
            traffic = null
            return@LaunchedEffect
        }
        if (page !in listOf(0, 3)) return@LaunchedEffect
        while (true) {
            val result = withContext(Dispatchers.IO) { controller.trafficSnapshot() }
            result.onSuccess { traffic = it; trafficError = "" }
                .onFailure { traffic = null; trafficError = it.localizedMessage ?: "Estadísticas no disponibles" }
            delay(5000)
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
                        Text("V4.5 • Proveedores WireGuard", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(pages[page], fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Cyan)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (page) {
                        0 -> {
                            val active = tunnelStatus == TunnelStatus.CONNECTED
                            val statusText = when (tunnelStatus) {
                                TunnelStatus.CONNECTED -> "Túnel activo"
                                TunnelStatus.CONNECTING -> "Conectando…"
                                TunnelStatus.ERROR -> "Error de conexión"
                                else -> "VPN desconectada"
                            }
                            val availableProviders = remember(catalogVersion) { catalog.list() }
                            var providerExpanded by remember { mutableStateOf(false) }
                            val chosenProvider = availableProviders.firstOrNull { it.id == selectedServerId }
                            TmcStatusCard(status = statusText, detail = message)
                            Spacer(Modifier.height(18.dp))
                            TmcConnectionButton(
                                connected = active,
                                enabled = hasProfile && !busy && tunnelStatus != TunnelStatus.CONNECTING,
                                onClick = { toggleConnection() }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (active) "%02d:%02d:%02d".format(java.util.Locale.US, sessionSeconds / 3600, (sessionSeconds / 60) % 60, sessionSeconds % 60) else "00:00:00",
                                color = TmcColors.TextPrimary,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Tiempo observado en esta pantalla", color = TmcColors.TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("SERVIDOR SELECCIONADO", color = TmcColors.Accent, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                            Box(Modifier.fillMaxWidth()) {
                                TmcServerCard(
                                    name = chosenProvider?.name ?: "Seleccionar proveedor / servidor",
                                    subtitle = "WireGuard • Perfil autorizado",
                                    selected = chosenProvider != null,
                                    enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy,
                                    onClick = { providerExpanded = true }
                                )
                                DropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                                    if (availableProviders.isEmpty()) {
                                        DropdownMenuItem(text = { Text("Importa primero un perfil .conf") }, onClick = { providerExpanded = false })
                                    }
                                    availableProviders.forEach { provider ->
                                        DropdownMenuItem(
                                            text = { Text(provider.name) },
                                            onClick = {
                                                providerExpanded = false
                                                if (tunnelStatus == TunnelStatus.DISCONNECTED && !busy) {
                                                    runCatching { controller.importProfile(profileStore.load(provider.id)).getOrThrow() }
                                                        .onSuccess {
                                                            selectedServerId = provider.id
                                                            hasProfile = true
                                                            message = "Proveedor seleccionado: " + provider.name
                                                        }
                                                        .onFailure {
                                                            selectedServerId = null
                                                            hasProfile = false
                                                            message = "Error al cargar proveedor: " + (it.localizedMessage ?: "perfil inválido")
                                                        }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TmcTrafficCard("RECIBIDO", traffic?.let { formatTraffic(it.receivedBytes) }, Modifier.weight(1f))
                                TmcTrafficCard("ENVIADO", traffic?.let { formatTraffic(it.sentBytes) }, Modifier.weight(1f))
                            }
                            if (active && traffic == null) Text(trafficError, color = TmcColors.TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { picker.launch(arrayOf("*/*")) },
                                enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Importar perfil .conf") }
                            Text("Los perfiles requieren una conexión a Internet existente. WireGuard no proporciona datos gratuitos.", color = TmcColors.TextSecondary, fontSize = 12.sp)
                            Info("Red", network)
                            Info("Recuperación de red", lastRecoveryMessage)
                        }
                        1 -> {
                            var serverQuery by remember { mutableStateOf("") }
                            var onlyFavorites by remember { mutableStateOf(false) }
                            val servers = remember(catalogVersion) {
                                catalog.list().sortedWith(
                                    compareByDescending<com.tmc.vpn.servers.VpnServer> { it.favorite }
                                        .thenBy { it.name.lowercase(java.util.Locale.getDefault()) }
                                )
                            }
                            val visibleServers = remember(servers, onlyFavorites, serverQuery) {
                                val query = serverQuery.trim()
                                servers.filter {
                                    (!onlyFavorites || it.favorite) &&
                                        (it.name.contains(query, ignoreCase = true) ||
                                            it.country.contains(query, ignoreCase = true))
                                }
                            }
                            Text("MIS SERVIDORES", color = TmcColors.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            Text("Perfiles WireGuard guardados en este dispositivo", color = TmcColors.TextSecondary, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = serverQuery,
                                onValueChange = { serverQuery = it },
                                label = { Text("Buscar servidor") },
                                placeholder = { Text("Nombre o país") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Solo favoritos", color = TmcColors.TextPrimary, modifier = Modifier.weight(1f))
                                Switch(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it })
                            }
                            Text("${visibleServers.size} de ${servers.size} perfiles", color = TmcColors.TextSecondary, modifier = Modifier.fillMaxWidth())
                            if (servers.isEmpty()) {
                                TmcStatusCard("Sin servidores", "Importa un perfil WireGuard .conf autorizado para comenzar.")
                            } else if (visibleServers.isEmpty()) {
                                TmcStatusCard("Sin coincidencias", "Prueba otra búsqueda o desactiva el filtro de favoritos.")
                            }
                            visibleServers.forEach { server ->
                                Spacer(Modifier.height(8.dp))
                                TmcServerCard(
                                    name = server.name,
                                    subtitle = "${server.country} • " + if (server.kind == ServerKind.FREE) "Perfil gratuito guardado" else "Perfil personalizado",
                                    selected = selectedServerId == server.id,
                                    enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy,
                                    onClick = {
                                        if (tunnelStatus == TunnelStatus.DISCONNECTED && !busy) {
                                            runCatching { controller.importProfile(profileStore.load(server.id)).getOrThrow() }
                                                .onSuccess {
                                                    selectedServerId = server.id
                                                    hasProfile = true
                                                    message = "Perfil seleccionado: " + server.name
                                                }
                                                .onFailure {
                                                    message = "No se pudo cargar el perfil: " + (it.localizedMessage ?: "error")
                                                }
                                        }
                                    }
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            catalog.toggleFavorite(server.id)
                                            catalogVersion++
                                        }
                                    ) { Text(if (server.favorite) "★ Favorito" else "☆ Favorito") }
                                    TextButton(
                                        enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy,
                                        onClick = {
                                            if (tunnelStatus == TunnelStatus.DISCONNECTED && !busy) {
                                                profileStore.delete(server.id)
                                                catalog.remove(server.id)
                                                catalogVersion++
                                                if (selectedServerId == server.id) {
                                                    selectedServerId = null
                                                    hasProfile = false
                                                }
                                                message = "Perfil eliminado: " + server.name
                                            }
                                        }
                                    ) { Text("Eliminar") }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { picker.launch(arrayOf("*/*")) },
                                enabled = tunnelStatus == TunnelStatus.DISCONNECTED && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Importar configuración WireGuard (.conf)") }
                            Text("Para cambiar o eliminar un perfil, desconecta primero la VPN. La búsqueda y los favoritos no alteran claves ni configuraciones.", color = TmcColors.TextSecondary, fontSize = 12.sp)
                            Info("Disponibilidad", "La lista muestra perfiles guardados; no confirma latencia, velocidad ni disponibilidad del servidor.")
                        }
                        2 -> {
                            Info("Tipo de red", network)
                            Info("Validación de Internet", diagnostics?.internet ?: "No disponible")
                            Info("DNS configurados", diagnostics?.dns ?: "No disponible")
                            Info("Ruta de conexión", diagnostics?.route ?: "No disponible")
                            Info("Estado WireGuard", when (tunnelStatus) { TunnelStatus.CONNECTED -> "Conectado"; TunnelStatus.CONNECTING -> "Conectando"; TunnelStatus.ERROR -> "Error; revisa la pantalla Inicio"; else -> "Desconectado" })
                            Button(onClick = { refresh++ }) { Text("Actualizar diagnóstico") }
                            Info("Nota", "La validación del sistema no garantiza ausencia de fugas DNS. La latencia real requiere una prueba activa.")
                            Info("Historial de esta sesión", if (connectionHistory.isEmpty()) "Todavía no hay eventos de conexión." else connectionHistory.joinToString("\n"))
                            Button(onClick = { connectionHistory = emptyList(); historyPreferences.edit().remove("events").apply() }, enabled = connectionHistory.isNotEmpty()) { Text("Limpiar historial") }
                            Info("Privacidad del historial", "Guardado localmente entre sesiones (máximo 20 eventos), sin claves, perfiles ni direcciones IP. Puedes borrarlo aquí.")
                        }
                        3 -> {
                            val active = tunnelStatus == TunnelStatus.CONNECTED
                            val chosenServer = remember(catalogVersion, selectedServerId) {
                                catalog.list().firstOrNull { it.id == selectedServerId }
                            }
                            Text("TRÁFICO WIREGUARD", color = TmcColors.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            TmcStatusCard(
                                status = when (tunnelStatus) {
                                    TunnelStatus.CONNECTED -> "Conectado"
                                    TunnelStatus.CONNECTING -> "Conectando…"
                                    TunnelStatus.ERROR -> "Error de conexión"
                                    else -> "Desconectado"
                                },
                                detail = chosenServer?.name ?: "Sin perfil seleccionado"
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TmcTrafficCard("RECIBIDO (RX)", if (active) traffic?.let { formatTraffic(it.receivedBytes) } else null, Modifier.weight(1f))
                                TmcTrafficCard("ENVIADO (TX)", if (active) traffic?.let { formatTraffic(it.sentBytes) } else null, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Info(
                                "Tiempo observado en la aplicación",
                                if (active) "%02d:%02d:%02d".format(
                                    java.util.Locale.US, sessionSeconds / 3600,
                                    (sessionSeconds / 60) % 60, sessionSeconds % 60
                                ) else "No hay sesión activa"
                            )
                            if (active && traffic == null) {
                                Info("Estado de lectura", trafficError)
                            }
                            Info(
                                "Origen de los datos",
                                "Contadores reales del túnel WireGuard. Se actualizan cada 3 segundos al visualizar Inicio o Estadísticas. No representan el consumo total de datos móviles."
                            )
                            Info("Alcance del tiempo", "Tiempo observado desde que esta pantalla de la aplicación detectó la conexión; no es un historial persistente del túnel.")
                        }
                        4 -> {
                            Info("WireGuard", if (hasProfile) "Perfil válido importado en memoria" else "Sin perfil")
                            Info("VPN siempre activa", "Configúrala en Ajustes de Android > VPN después de conectar correctamente.")
                            Info("Bloquear sin VPN", "Activa esta protección en Android si necesitas evitar tráfico fuera del túnel.")
                            Info("Privacidad", "Perfiles cifrados con clave no exportable de Android Keystore.")
                        }
                        5 -> {
                            Text("PREFERENCIAS", color = TmcColors.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = TmcColors.Surface)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Recuperación automática", color = TmcColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                            Text("Preferencia guardada en este dispositivo", color = TmcColors.TextSecondary, fontSize = 12.sp)
                                        }
                                        Switch(checked = autoRecovery, onCheckedChange = { enabled ->
                                            autoRecovery = enabled
                                            recoveryPreferences.edit().putBoolean("enabled", enabled).apply()
                                        })
                                    }
                                    HorizontalDivider(color = Color(0xFF29425F))
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Preferencia de notificaciones", color = TmcColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                            Text("No cambia los permisos de Android", color = TmcColors.TextSecondary, fontSize = 12.sp)
                                        }
                                        Switch(checked = notificationPreference, onCheckedChange = { notificationPreference = it })
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            TmcStatusCard(
                                status = "Seguridad WireGuard",
                                detail = "Los perfiles guardados están cifrados con Android Keystore. Configura VPN siempre activa y Bloquear sin VPN desde los ajustes de Android."
                            )
                            Spacer(Modifier.height(12.dp))
                            Info(
                                "Recuperación de red",
                                "La vigilancia funciona mientras la actividad está visible. No es un servicio permanente en segundo plano ni un kill switch."
                            )
                            Info("Versión instalada", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "No disponible")
                            Info("Interfaz en desarrollo", "TMC VPN V4.6-P5 • APK de prueba, sin publicación de versión final.")
                            Button(onClick = { welcome = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Pantalla de bienvenida")
                            }
                        }
                        else -> {
                            Info("Cuenta", "Sin registro obligatorio.")
                            Info("Gratis", "Importación de un perfil WireGuard personal.")
                            Info("Pro", "Concepto futuro, sin suscripciones ni pagos.")
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF29425F))
                NavigationBar(containerColor = TmcColors.Surface) {
                    listOf(0, 1, 3, 5).forEach { destination ->
                        val label = pages[destination]
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { page = destination; menuExpanded = false },
                            icon = { Text(when (destination) {
                                0 -> "⌂"
                                1 -> "◈"
                                3 -> "▥"
                                else -> "⚙"
                            }, fontSize = 20.sp) },
                            label = { Text(label, fontSize = 10.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TmcColors.Accent,
                                selectedTextColor = TmcColors.Accent,
                                indicatorColor = TmcColors.Background,
                                unselectedIconColor = TmcColors.TextSecondary,
                                unselectedTextColor = TmcColors.TextSecondary
                            )
                        )
                    }
                    NavigationBarItem(
                        selected = page in listOf(2, 4, 6),
                        onClick = { menuExpanded = !menuExpanded },
                        icon = { Text("☰", fontSize = 20.sp) },
                        label = { Text("Más", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TmcColors.Accent,
                            selectedTextColor = TmcColors.Accent,
                            indicatorColor = TmcColors.Background,
                            unselectedIconColor = TmcColors.TextSecondary,
                            unselectedTextColor = TmcColors.TextSecondary
                        )
                    )
                }
                AnimatedVisibility(visible = menuExpanded) {
                    Column(Modifier.fillMaxWidth().background(TmcColors.Surface, RoundedCornerShape(16.dp)).padding(8.dp)) {
                        listOf(2, 4, 6).forEach { destination ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { page = destination; menuExpanded = false }
                            ) { Text(pages[destination], color = if (page == destination) TmcColors.Accent else TmcColors.TextPrimary) }
                        }
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
