package com.tmc.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

private val Navy = Color(0xFF081426)
private val Panel = Color(0xFF172A43)
private val Cyan = Color(0xFF25D8F6)
private val Green = Color(0xFF4BE6B5)
private val Red = Color(0xFFE72D4A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TmcApp(this) }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.tmc_shield), contentDescription = "Escudo TMC VPN", modifier = Modifier.size(118.dp))
        Text("TMC VPN", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Conexión privada • Control transparente", color = Cyan, fontSize = 12.sp)
    }
}

@Composable
private fun TmcApp(context: Context) {
    var welcome by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val network = remember(refresh) { networkType(context) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Navy, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Navy).padding(18.dp)) {
            if (welcome) {
                Spacer(Modifier.weight(1f))
                Brand(Modifier.fillMaxWidth())
                Spacer(Modifier.height(30.dp))
                Text("Bienvenido a TMC VPN", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Tu conexión VPN estará disponible cuando configures un servidor WireGuard autorizado. Una VPN no proporciona datos móviles gratuitos.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.LightGray)
                Spacer(Modifier.weight(1f))
                Button(onClick = { welcome = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan)) { Text("Comenzar", color = Navy) }
                Spacer(Modifier.height(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.tmc_shield), contentDescription = "Logo TMC VPN", modifier = Modifier.size(64.dp))
                    Column {
                        Text("TMC VPN", fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text("Versión 0.2.0 • Servidor pendiente", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Inicio", "Servidores", "Red", "Ajustes").forEachIndexed { index, label ->
                        Button(onClick = { tab = index }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.buttonColors(containerColor = if (tab == index) Cyan else Panel)) {
                            Text(label, fontSize = 11.sp, color = if (tab == index) Navy else Color.White, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (tab) {
                        0 -> {
                            Spacer(Modifier.height(18.dp))
                            Image(painterResource(R.drawable.tmc_shield), contentDescription = null, modifier = Modifier.size(160.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("DESCONECTADO", color = Red, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("No existe un túnel VPN activo", color = Color.LightGray)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Conectar VPN") }
                            Spacer(Modifier.height(18.dp))
                            InfoCard("Servidor", "Sin configurar • No hay servidores de prueba ficticios")
                            InfoCard("Red actual", network)
                            InfoCard("Protección", "Inactiva. El tráfico no está protegido por TMC VPN.")
                        }
                        1 -> {
                            SectionTitle("Servidores")
                            InfoCard("Servidor gratuito", "Pendiente de aprovisionar. Recomendación: VPS Always Free compatible con WireGuard.")
                            InfoCard("Configuración necesaria", "IP pública, claves WireGuard y perfil privado del cliente. No publiques claves en GitHub.")
                            InfoCard("Conexión real", "Se habilitará cuando se integre y verifique el motor WireGuard.")
                        }
                        2 -> {
                            SectionTitle("Diagnóstico de red")
                            InfoCard("Transporte detectado", network)
                            Button(onClick = { refresh++ }) { Text("Actualizar") }
                            InfoCard("Alcance", "Esta prueba identifica el tipo de red; no comprueba acceso a Internet ni disponibilidad del servidor.")
                        }
                        else -> {
                            SectionTitle("Configuración")
                            InfoCard("Versión", "0.2.0 — interfaz y bienvenida")
                            InfoCard("Seguridad", "Sin servidor configurado y sin túnel activo.")
                            InfoCard("Privacidad", "No se guardan claves privadas ni perfiles VPN en esta versión.")
                            Button(onClick = { welcome = true }) { Text("Ver bienvenida") }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(value: String) {
    Text(value, modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable private fun InfoCard(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(Panel, RoundedCornerShape(15.dp)).padding(17.dp)) {
        Text(title, color = Green, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Color.White, fontSize = 14.sp)
    }
}

private fun networkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red activa"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN externa activa"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Otra conexión"
    }
}
