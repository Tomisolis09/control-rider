package com.repartidor.control.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.repartidor.control.AppState
import com.repartidor.control.Respaldo
import com.repartidor.control.exportarCsv
import java.io.File
import java.time.LocalDate

val SECCIONES_MAS = listOf(
    "Gastos fijos" to "🏷  Gastos fijos del hogar",
    "Descuentos" to "🏦  Seguro de la app y crédito",
    "Combustible" to "⛽  Combustible y costo por km",
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
    var pendiente by remember { mutableStateOf<Uri?>(null) }

    // guardar una copia a mano en el lugar que elijas (Descargas, Drive, etc.)
    val crear = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Respaldo.exportar(app.db).toByteArray(Charsets.UTF_8)) }
                app.aviso = "Copia guardada ✔"
            } catch (e: Exception) {
                app.aviso = "No se pudo guardar la copia"
            }
        }
    }
    // elegir el archivo de una copia para restaurar
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendiente = uri
    }
    // elegir la carpeta de la copia automática
    val carpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                app.elegirCarpetaRespaldo(uri.toString())
                app.aviso = "Copia automática activada: se guarda sola cada vez que cargás algo"
            } catch (e: Exception) {
                app.aviso = "No se pudo usar esa carpeta. Elegí otra."
            }
        }
    }

    Pantalla {
        Titulo("Ajustes y datos")

        Panel("Apariencia") {
            Selector("Tema", nombres, nombres[app.tema.coerceIn(0, 2)], { app.cambiarTema(nombres.indexOf(it)) })
        }

        Panel("Copia de seguridad") {
            Text("Si desinstalás la app o cambiás de teléfono, Android borra lo que la app tiene guardado adentro. Para que no se pierda nada, la app guarda una copia en una carpeta que elegís vos: en tu teléfono (Documentos o Descargas) o en Google Drive.")
            Text(if (app.respaldoActivo) "✔ Copia automática activada" else "Copia automática desactivada",
                color = if (app.respaldoActivo) Paleta.Verde else Paleta.Ambar)
            if (app.estadoRespaldo.isNotEmpty()) Subtexto(app.estadoRespaldo)
            Boton(if (app.respaldoActivo) "Cambiar carpeta de la copia" else "Activar copia automática (elegir carpeta)", { carpeta.launch(null) })
            Subtexto("Elegí una carpeta que no vayas a borrar. Con Google Drive la copia queda en la nube y la recuperás aunque pierdas el teléfono.")
            if (app.respaldoActivo) {
                Boton("Guardar copia ahora", { app.respaldarAsync(); app.aviso = "Guardando la copia…" }, tipo = 1)
                Boton("Desactivar copia automática", { app.elegirCarpetaRespaldo(null) }, tipo = 1)
            }
            Boton("Guardar una copia en otro lugar", { crear.launch("ControlRider-copia-" + LocalDate.now() + ".json") }, tipo = 1)
            Boton("Restaurar una copia", { abrir.launch(arrayOf("application/json", "text/plain", "*/*")) }, tipo = 1)
            Subtexto("Al reinstalar la app: tocá Restaurar una copia y elegí el archivo ControlRider-respaldo.json de tu carpeta o de Drive. Se reemplazan los datos actuales por los de la copia.")
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
            Subtexto("El CSV sirve para mirar tus datos en Excel; para recuperarlos en la app usá la copia de seguridad de arriba.")
        }

        Panel("Cómo se calcula") {
            Text("Saldo = pago base + adicionales + adicional por grupo + bono − efectivo recaudado. Si es positivo, es lo que cobrás; si es negativo, se lo debés a la app.")
            Text("Las propinas no se suman ni se restan: se muestran aparte y se cobran completas.")
            Text("Punto de equilibrio = gastos fijos del hogar (por día trabajado) + gasto de trabajo promedio + fondo de repuestos, comparado con lo que facturás en el día (incluye propinas).")
        }

        Subtexto("Control Rider · versión 1.0")
    }

    val elegido = pendiente
    Confirmar(
        elegido != null, "Restaurar copia",
        "Se reemplazan TODOS los datos actuales por los de la copia. Lo que cargaste después de esa copia se pierde. ¿Restaurar?",
        onOk = {
            if (elegido != null) {
                try {
                    val texto = ctx.contentResolver.openInputStream(elegido)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    val n = Respaldo.importar(app.db, texto)
                    app.cambio("Copia restaurada: $n registros recuperados ✔")
                } catch (e: Exception) {
                    app.aviso = "Ese archivo no es una copia válida de Control Rider"
                }
            }
            pendiente = null
        },
        onCancel = { pendiente = null }
    )
}
