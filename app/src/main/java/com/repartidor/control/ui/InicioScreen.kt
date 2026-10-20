package com.repartidor.control.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.DIAS
import com.repartidor.control.DIAS_CORTOS
import com.repartidor.control.diaSemana
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
    val c = app.calc

    val tHoy = remember(v) { c.totales(hoy, hoy) }
    val tSem = remember(v) { val p = periodoSemana(hoy, app.db.diaCierre); c.totales(p.first, p.second) }
    val tMes = remember(v) { c.totales(hoy.withDayOfMonth(1), finDeMes(hoy)) }
    val descSem = remember(v) { c.descuentoSemana(periodoSemana(hoy, app.db.diaCierre).second).total }
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

    Pantalla {
        val hora = java.time.LocalTime.now().hour
        Titulo(
            if (hora < 12) "Buen día" else if (hora < 20) "Buenas tardes" else "Buenas noches",
            "${DIAS[diaSemana(hoy)]} ${fmtFecha(hoy)}"
        )

        FilaKpis {
            SaldoKpi("Hoy", tHoy.neto, peso())
            SaldoKpi("Semana", tSem.neto - descSem, peso())
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
                        (if (cb.descuentos > 0) " · descuentos ${money(cb.descuentos)}" else ""))
                }
            }
            Subtexto("Cerrada = ya terminó · En curso = parcial · Proyectada (~) = promedio de tus últimas semanas.")
        }

        Panel("Saldo por día · últimos 14 días") { GraficoBarras(saldo14, Paleta.Acento) }
        Panel("Dónde se va tu plata · este mes") { GraficoAnillo(gastosMes) }
    }
}
