package com.repartidor.control.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.DIAS
import com.repartidor.control.DIAS_CORTOS
import com.repartidor.control.diaSemana
import com.repartidor.control.fechaCobro
import com.repartidor.control.finDeMes
import com.repartidor.control.fmtCorto
import com.repartidor.control.fmtFecha
import com.repartidor.control.money
import com.repartidor.control.money0
import com.repartidor.control.periodoSemana
import java.time.LocalDate

@Composable
fun InicioScreen(app: AppState) {
    val hoy = LocalDate.now()
    val v = app.version
    val ctx = LocalContext.current
    val permisos = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    // restaurar en un paso desde la carpeta donde estaba la copia (al reinstalar)
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, permisos) } catch (e: Exception) { }
            val r = app.restaurarDesdeCarpeta(uri)
            if (r != "ok") app.aviso = r
        }
    }
    // activar la copia automática desde el aviso de Inicio
    val activar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, permisos)
                app.elegirCarpetaRespaldo(uri.toString())
                app.cambio("Copia automática activada: tus datos se guardan solos ✔")
            } catch (e: Exception) {
                app.aviso = "No se pudo usar esa carpeta. Elegí otra."
            }
        }
    }
    val c = app.calc

    val tHoy = remember(v) { c.totales(hoy, hoy) }
    val tSem = remember(v) { val p = periodoSemana(hoy, app.db.diaCierre); c.totales(p.first, p.second) }
    val tMes = remember(v) { c.totales(hoy.withDayOfMonth(1), finDeMes(hoy)) }
    val cuentaSem = remember(v) { c.cuentaSemana(periodoSemana(hoy, app.db.diaCierre).second) }
    val descMes = remember(v) { c.descuentosPeriodo(hoy.withDayOfMonth(1), finDeMes(hoy)).total }
    val cobros = remember(v) { c.calendarioCobros(hoy) }
    val alertas = remember(v) { c.alertas(hoy) }
    val saldo14 = remember(v) {
        (13 downTo 0).map { i ->
            val d = hoy.minusDays(i.toLong())
            fmtCorto(d) to c.totales(d, d).neto
        }
    }
    val gastosMes = remember(v) {
        val d0 = hoy.withDayOfMonth(1).toString()
        val d1 = finDeMes(hoy).toString()
        val acum = HashMap<String, Double>()
        for (tabla in listOf("gastos", "gastos_fijos")) {
            for (r in app.db.rows("SELECT categoria c, SUM(monto) m FROM $tabla WHERE fecha BETWEEN ? AND ? GROUP BY categoria", d0, d1)) {
                acum[r.s("c")] = (acum[r.s("c")] ?: 0.0) + r.d("m")
            }
        }
        val mant = app.db.one("SELECT COALESCE(SUM(costo),0) c FROM mantenimiento WHERE fecha BETWEEN ? AND ?", d0, d1)!!.d("c")
        if (mant > 0) acum["Mantenimiento"] = (acum["Mantenimiento"] ?: 0.0) + mant
        val orden = acum.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val top = orden.take(6)
        val resto = orden.drop(6).sumOf { it.second }
        if (resto > 0) top + ("Otros" to resto) else top
    }

    val sinDatos = remember(v) {
        app.db.one("SELECT COUNT(*) c FROM diario")!!.i("c") == 0 && app.db.one("SELECT 1 x FROM meta WHERE id=1") == null
    }

    Pantalla {
        val hora = java.time.LocalTime.now().hour
        Titulo(
            if (hora < 12) "Buen día" else if (hora < 20) "Buenas tardes" else "Buenas noches",
            "${DIAS[diaSemana(hoy)]} ${fmtFecha(hoy)}"
        )

        if (sinDatos) {
            Panel("¿Reinstalaste la app?", acento = Paleta.Ambar) {
                Text("Si tenías la copia automática activada, tus datos están en la carpeta que elegiste (en tu teléfono o en Google Drive). Elegila y se recupera todo de una vez.", fontSize = 14.sp)
                Boton("Recuperar mis datos (elegir carpeta)", { restaurar.launch(null) }, Modifier.fillMaxWidth())
                Subtexto("Si es tu primera vez, empezá cargando tu primer turno en Diario.")
            }
        } else if (!app.respaldoActivo) {
            Panel("Protegé tus datos", acento = Paleta.Ambar) {
                Text("Todavía no tenés copia automática. Elegí una carpeta (mejor si es de Google Drive) y tus datos se guardan solos: si desinstalás la app o cambiás de teléfono, los recuperás.", fontSize = 14.sp)
                Boton("Activar copia automática", { activar.launch(null) }, Modifier.fillMaxWidth())
            }
        }
        val finSem = periodoSemana(hoy, app.db.diaCierre).second
        val cobroSem = fechaCobro(finSem, app.db.diaCobro)
        Hero(
            if (cuentaSem.total < -0.005) "Esta semana · debés a la app" else "Esta semana · a cobrar",
            cuentaSem.total,
            if (cuentaSem.total < -0.005) "Esta deuda pasa a la semana siguiente"
            else "Cobrás el ${DIAS[diaSemana(cobroSem)].lowercase()} ${fmtCorto(cobroSem)}" +
                (if (cuentaSem.arrastre < -0.005) " · incluye deuda anterior" else "")
        )
        FilaKpis {
            SaldoKpi("Hoy", tHoy.neto, peso())
            SaldoKpi("Mes", tMes.neto - descMes, peso())
        }
        FilaKpis {
            Kpi("Pedidos del mes", tMes.pedidos.toString(), Paleta.Ambar, peso())
            Kpi("Prom. por pedido", money0(tMes.promedio), Paleta.Acento, peso())
        }
        FilaKpis {
            KpiMonto("Efectivo recaudado · mes", tMes.efectivo, Paleta.Violeta, peso())
            KpiMonto("Gastos del mes", tMes.gastos, Paleta.Rojo, peso())
        }

        Panel("Alertas") {
            if (alertas.isEmpty()) {
                Text("✔ Todo en orden: no hay alertas.", fontSize = 14.sp, color = Paleta.Verde)
            } else {
                for ((nivel, texto) in alertas) Aviso(nivel, texto)
            }
        }

        Panel("Calendario de cobros") {
            for (cb in cobros) {
                val saldoTxt = if (cb.neto >= 0) money(cb.neto) else "Debés " + money(-cb.neto)
                val pref = if (cb.estado == "Proyectada") "~ " else ""
                Column {
                    FilaDato(
                        "${DIAS_CORTOS[diaSemana(cb.cobro)]} ${fmtCorto(cb.cobro)} · ${cb.estado}",
                        pref + saldoTxt,
                        if (cb.neto < 0) Paleta.Rojo else if (cb.estado == "Cerrada") Paleta.Verde else Paleta.Ambar,
                        true
                    )
                    Subtexto("Semana ${fmtCorto(cb.ini)} al ${fmtCorto(cb.fin)} · propinas $pref${money(cb.propinas)}" +
                        (if (cb.descuentos > 0) " · descuentos ${money(cb.descuentos)}" else "") +
                        (if (cb.arrastre < -0.005) " · deuda anterior ${money(-cb.arrastre)}" else ""))
                }
            }
            Subtexto("Cerrada = ya terminó · En curso = parcial · Proyectada (~) = promedio de tus últimas semanas.")
        }

        Panel("Saldo por día · últimos 14 días") { GraficoBarras(saldo14, Paleta.Acento) }
        Panel("Dónde se va tu plata · este mes") { GraficoAnillo(gastosMes) }
    }
}
