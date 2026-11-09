package com.tmc.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0D192B)
private val Panel = Color(0xFF1B2B42)
private val Mint = Color(0xFF45E0B3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TmcApp(this) }
    }
}

@Composable
private fun TmcApp(context: Context) {
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val network = remember(refresh) { networkType(context) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Navy, surface = Panel)) {
        Column(Modifier.fillMaxSize().background(Navy).padding(20.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("⬡  TMC VPN", color = Mint, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Versión inicial 0.1.0", color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Inicio", "Servidores", "Diagnóstico", "Ajustes").forEachIndexed { i, label ->
                    Button(onClick = { tab = i }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 7.dp), colors = ButtonDefaults.buttonColors(containerColor = if (tab == i) Mint else Panel)) {
                        Text(label, fontSize = 10.sp, color = if (tab == i) Navy else Color.White, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                when (tab) {
                    0 -> {
                        Spacer(Modifier.height(30.dp))
                        Text("◉", fontSize = 86.sp, color = Mint)
                        Text("VPN desconectada", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text("No hay un servidor configurado", color = Color.LightGray)
                        Spacer(Modifier.height(30.dp))
                        Button(onClick = {}, enabled = false) { Text("Conectar (próxima fase)") }
                        Spacer(Modifier.height(18.dp))
                        InfoCard("Red actual", network)
                        InfoCard("Estado del túnel", "No disponible en esta versión")
                    }
                    1 -> {
                        SectionTitle("Servidores VPN")
                        InfoCard("Servidores disponibles", "Todavía no se ha configurado ningún servidor.")
                        InfoCard("Próxima fase", "Importación de perfiles WireGuard autorizados.")
                    }
                    2 -> {
                        SectionTitle("Diagnóstico de red")
                        InfoCard("Tipo de conexión", network)
                        Button(onClick = { refresh++ }) { Text("Actualizar diagnóstico") }
                        Text("Esta comprobación detecta el transporte de red; no verifica acceso real a Internet.", color = Color.LightGray, fontSize = 12.sp)
                    }
                    else -> {
                        SectionTitle("Configuración")
                        InfoCard("Aplicación", "TMC VPN 0.1.0 — Fase 1")
                        InfoCard("Privacidad", "No se solicita acceso VPN ni se recopilan cuentas o contraseñas.")
                        InfoCard("Seguridad", "No hay túnel VPN activo. El tráfico no está protegido por TMC VPN.")
                    }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(value: String) {
    Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 18.dp))
}

@Composable private fun InfoCard(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(Panel, RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text(title, color = Mint, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Color.White, fontSize = 14.sp)
    }
}

private fun networkType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red activa detectada"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN externa activa"
        else -> "Otra conexión"
    }
}
