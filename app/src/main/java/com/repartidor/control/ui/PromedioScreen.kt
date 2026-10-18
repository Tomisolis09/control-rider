package com.repartidor.control.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.repartidor.control.AppState
import com.repartidor.control.finDeMes
import com.repartidor.control.fmtCorto
import com.repartidor.control.money
import com.repartidor.control.money0
import com.repartidor.control.periodoSemana
import java.time.LocalDate

private class Sem(val ini: LocalDate, val fin: LocalDate, var n: Int = 0, var total: Double = 0.0) {
    val prom: Double get() = if (n > 0) total / n else 0.0
}

@Composable
fun PromedioScreen(app: AppState) {
    val v = app.version
    val hoy = LocalDate.now()

    val semanas = remember(v) {
        val mapa = HashMap<LocalDate, Sem>()
        val cierre = app.db.diaCierre
        for (r in app.db.rows("SELECT fecha, SUM(pedidos) n, SUM(base+adicionales+grupo+COALESCE(bono,0)) t FROM diario GROUP BY fecha")) {
            val p = periodoSemana(LocalDate.parse(r.s("fecha")), cierre)
            val s = mapa.getOrPut(p.second) { Sem(p.first, p.second) }
            s.n += r.i("n")
            s.total += r.d("t")
        }
        mapa.values.sortedByDescending { it.fin }
    }
    val mes = remember(v) { app.calc.totales(hoy.withDayOfMonth(1), finDeMes(hoy)) }
    val nTot = semanas.sumOf { it.n }
    val tTot = semanas.sumOf { it.total }
    val conPedidos = semanas.filter { it.n > 0 }
    val mejor = conPedidos.maxByOrNull { it.prom }
    val ultimas = conPedidos.sortedBy { it.fin }.takeLast(8)

    Pantalla {
        Titulo("Promedio por pedido", "Base + adicionales + adicional por grupo + bono, sin propinas y sin descontar efectivo.")
        FilaKpis {
            KpiMonto("Mes actual", mes.promedio, Paleta.Acento, peso())
            KpiMonto("Histórico", if (nTot > 0) tTot / nTot else 0.0, Paleta.Violeta, peso())
        }
        FilaKpis {
            Kpi("Mejor semana", if (mejor != null) money0(mejor.prom) else "—", Paleta.Verde, peso())
            Kpi("Pedidos totales", nTot.toString(), Paleta.Ambar, peso())
        }

        Panel("Por semana") {
            if (semanas.isEmpty()) Subtexto("Todavía no hay turnos cargados.")
            for (s in semanas) {
                FilaDato(
                    "${fmtCorto(s.ini)} al ${fmtCorto(s.fin)} · ${s.n} pedidos",
                    money(s.prom) + " c/u"
                )
                Subtexto("Total por pedidos: ${money(s.total)}")
            }
        }
        Panel("Promedio por pedido · últimas 8 semanas") {
            GraficoBarras(ultimas.map { fmtCorto(it.fin) to it.prom }, Paleta.Violeta)
        }
    }
}
