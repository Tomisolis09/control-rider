package com.repartidor.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.ui.AjustesScreen
import com.repartidor.control.ui.DiarioScreen
import com.repartidor.control.ui.GastosFijosScreen
import com.repartidor.control.ui.InicioScreen
import com.repartidor.control.ui.MantenimientoScreen
import com.repartidor.control.ui.MasScreen
import com.repartidor.control.ui.MetaScreen
import com.repartidor.control.ui.Paleta
import com.repartidor.control.ui.PromedioScreen
import com.repartidor.control.ui.RecordatoriosScreen
import com.repartidor.control.ui.RendimientoScreen
import com.repartidor.control.ui.RepartidorTheme
import com.repartidor.control.ui.ResumenScreen
import com.repartidor.control.ui.SECCIONES_MAS
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = AppState(this)
        setContent {
            RepartidorTheme(app.tema) {
                Raiz(app)
            }
        }
    }
}

private class Destino(val clave: String, val icono: String, val titulo: String)

private val DESTINOS = listOf(
    Destino("Inicio", "🏠", "Inicio"),
    Destino("Diario", "📝", "Diario"),
    Destino("Resumen", "📅", "Resumen"),
    Destino("Meta", "🎯", "Meta"),
    Destino("Más", "☰", "Más")
)

@Composable
private fun Raiz(app: AppState) {
    var actual by remember { mutableStateOf("Inicio") }
    var sub by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = sub != null) { sub = null }

    LaunchedEffect(app.aviso) {
        val m = app.aviso
        if (m != null) {
            snackbar.showSnackbar(m)
            app.aviso = null
        }
    }

    // insignias de la barra inferior
    val hoy = LocalDate.now()
    val v = app.version
    val ea = remember(v) { app.calc.estadoAhorro(hoy) }
    val alertasMas = remember(v) {
        app.calc.recordatorios(hoy).count { it.estado != "Al día" } +
            app.calc.vencimientos(hoy).count { it.estado != "Ok" }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                for (d in DESTINOS) {
                    val aviso: String? = when (d.clave) {
                        "Meta" -> if (ea != null && (ea.estado == "pendiente" || ea.estado == "parcial")) "!" else null
                        "Más" -> if (alertasMas > 0) alertasMas.toString() else null
                        else -> null
                    }
                    NavigationBarItem(
                        selected = actual == d.clave,
                        onClick = { actual = d.clave; sub = null },
                        icon = {
                            BadgedBox(badge = { if (aviso != null) Badge { Text(aviso) } }) {
                                Text(d.icono, fontSize = 20.sp)
                            }
                        },
                        label = { Text(d.titulo, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val s = sub
            if (actual == "Más" && s != null) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { sub = null }) { Text("‹ Volver") }
                        Text(SECCIONES_MAS.firstOrNull { it.first == s }?.second ?: s, fontSize = 14.sp)
                    }
                    Box(Modifier.fillMaxSize()) {
                        when (s) {
                            "Gastos fijos" -> GastosFijosScreen(app)
                            "Mantenimiento" -> MantenimientoScreen(app)
                            "Recordatorios" -> RecordatoriosScreen(app)
                            "Rendimiento" -> RendimientoScreen(app)
                            "Promedio" -> PromedioScreen(app)
                            else -> AjustesScreen(app)
                        }
                    }
                }
            } else {
                when (actual) {
                    "Inicio" -> InicioScreen(app)
                    "Diario" -> DiarioScreen(app)
                    "Resumen" -> ResumenScreen(app)
                    "Meta" -> MetaScreen(app)
                    else -> MasScreen(app) { sub = it }
                }
            }
        }
    }
}
