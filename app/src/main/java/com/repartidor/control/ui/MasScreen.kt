package com.repartidor.control.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.repartidor.control.AppState
import com.repartidor.control.exportarCsv
import java.io.File

val SECCIONES_MAS = listOf(
    "Gastos fijos" to "🏷  Gastos fijos del hogar",
    "Mantenimiento" to "🔧  Mantenimiento del vehículo",
    "Recordatorios" to "🔔  Recordatorios y vencimientos",
    "Rendimiento" to "⏱  Rendimiento por hora",
    "Promedio" to "📊  Promedio por pedido",
    "Ajustes" to "⚙  Ajustes y datos"
)

@Composable
fun MasScreen(app: AppState, abrir: (String) -> Unit) {
    val alertas = app.calc.alertas(java.time.LocalDate.now()).size
    Pantalla {
        Titulo("Más", "Todo lo demás de tu control")
        for ((clave, titulo) in SECCIONES_MAS) {
            Boton(
                if (clave == "Recordatorios" && alertas > 0) "$titulo  ($alertas aviso${if (alertas == 1) "" else "s"})" else titulo,
                { abrir(clave) }, Modifier.fillMaxWidth(), 1
            )
        }
    }
}

@Composable
fun AjustesScreen(app: AppState) {
    val ctx = LocalContext.current
    val nombres = listOf("Según el sistema", "Claro", "Oscuro")

    Pantalla {
        Titulo("Ajustes y datos")

        Panel("Apariencia") {
            Selector("Tema", nombres, nombres[app.tema.coerceIn(0, 2)], { app.cambiarTema(nombres.indexOf(it)) })
        }

        Panel("Tus datos") {
            Text("Exportá todo (turnos, gastos, mantenimiento, aportes y vencimientos) en un archivo CSV que se abre en Excel.")
            Boton("Exportar datos (CSV)", {
                val archivo = File(ctx.cacheDir, "repartidor_export.csv")
                archivo.writeText("\uFEFF" + exportarCsv(app.db), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", archivo)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/csv"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.putExtra(Intent.EXTRA_SUBJECT, "Datos de Control Rider")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(intent, "Compartir datos"))
            })
            Subtexto("Copia de seguridad: los datos se guardan en el teléfono y, si tenés activada la copia de Google (Ajustes de Android → Google → Copia de seguridad), Android los respalda solo.")
        }

        Panel("Cómo se calcula") {
            Text("Saldo = pago base + adicionales + adicional por grupo + bono − efectivo recaudado. Si es positivo, es lo que cobrás; si es negativo, se lo debés a la app.")
            Text("Las propinas no se suman ni se restan: se muestran aparte y se cobran completas.")
            Text("Punto de equilibrio = gastos fijos del hogar (por día trabajado) + gasto de trabajo promedio + fondo de repuestos, comparado con lo que facturás en el día (incluye propinas).")
        }

        Subtexto("Control Rider · versión 1.0")
    }
}
