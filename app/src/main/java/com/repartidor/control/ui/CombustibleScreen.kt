package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.VEHICULOS
import com.repartidor.control.finDeMes
import com.repartidor.control.fmtFecha
import com.repartidor.control.money
import com.repartidor.control.num
import java.time.LocalDate
import java.util.Locale

private fun uno(x: Double): String = String.format(Locale.US, "%.1f", x).replace('.', ',')
private fun milesKm(n: Int): String = String.format(Locale.US, "%,d", n).replace(",", ".") + " km"

@Composable
fun CombustibleScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version

    var veh by remember { mutableStateOf(VEHICULOS[0]) }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var litros by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf<Long?>(null) }

    val filas = remember(v, veh) { db.rows("SELECT * FROM combustible WHERE vehiculo=? ORDER BY fecha DESC, id DESC", veh) }
    val consumo = remember(v, veh) { c.consumo(veh) }
    val gastoMes = remember(v, veh) {
        val hoy = LocalDate.now()
        db.one(
            "SELECT COALESCE(SUM(monto),0) m FROM combustible WHERE vehiculo=? AND fecha BETWEEN ? AND ?",
            veh, hoy.withDayOfMonth(1).toString(), finDeMes(hoy).toString()
        )!!.d("m")
    }

    fun guardar() {
        val l = num(litros)
        val m = num(monto)
        if (l == null || l <= 0) { app.aviso = "Ingresá los litros cargados"; return }
        if (m == null || m <= 0) { app.aviso = "Ingresá cuánto pagaste"; return }
        var kmV: Int? = null
        if (km.isNotBlank()) {
            val k = num(km)
            if (k == null) { app.aviso = "Los km deben ser un número positivo (ej. 12.500)"; return }
            kmV = k.toInt()
        }
        val gastoId = db.insertar(
            "INSERT INTO gastos(fecha,categoria,monto,nota) VALUES(?,?,?,?)",
            fecha.toString(), "Nafta", m, "Carga de combustible · ${uno(l)} L"
        )
        db.exec(
            "INSERT INTO combustible(fecha,vehiculo,litros,monto,km,gasto_id) VALUES(?,?,?,?,?,?)",
            fecha.toString(), veh, l, m, kmV, gastoId
        )
        if (kmV != null && kmV > c.kmActual(veh)) db.setCfg("km_$veh", kmV)
        litros = ""; monto = ""; km = ""
        app.cambio("Carga guardada · también quedó como gasto de nafta del ${fmtFecha(fecha)}")
    }

    Pantalla {
        Titulo("Combustible y costo por km", "Cargá cada recarga y calculá cuánto te cuesta cada kilómetro")
        Selector("Vehículo", VEHICULOS, veh, { veh = it })

        FilaKpis {
            Kpi("Consumo", if (consumo != null) uno(consumo.kmPorLitro) + " km/L" else "—", Paleta.Acento, peso())
            Kpi("Costo por km", if (consumo != null) money(consumo.costoKm) else "—", Paleta.Rojo, peso())
        }
        FilaKpis {
            Kpi(
                "Precio último litro",
                if (filas.isNotEmpty() && filas[0].d("litros") > 0) money(filas[0].d("monto") / filas[0].d("litros")) else "—",
                Paleta.Ambar, peso()
            )
            KpiMonto("Gasto del mes", gastoMes, Paleta.Violeta, peso())
        }
        if (consumo == null) {
            Subtexto("Para calcular el consumo cargá al menos 2 recargas con el km del odómetro. Llená el tanque en cada una para que el resultado sea exacto.")
        } else {
            Subtexto("Calculado con las últimas recargas: ${milesKm(consumo.km)} recorridos y ${uno(consumo.litros)} litros.")
        }

        Panel("Registrar recarga") {
            CampoFecha(fecha, { fecha = it })
            Dos {
                CampoDec("Litros", litros, { litros = it }, peso())
                CampoMonto("Total pagado", monto, { monto = it }, peso())
            }
            CampoNum("Km del odómetro", km, { km = it })
            Subtexto("Al guardar, la recarga también se suma a los gastos del día como \"Nafta\", así entra en el punto de equilibrio.")
            Boton("Guardar recarga", { guardar() })
        }

        Panel("Historial") {
            if (filas.isEmpty()) Subtexto("Todavía no hay recargas para este vehículo.")
            for (r in filas) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${uno(r.d("litros"))} L · ${money(r.d("monto"))}", fontSize = 14.sp)
                        val ppl = if (r.d("litros") > 0) " · " + money(r.d("monto") / r.d("litros")) + "/L" else ""
                        val kmTxt = if (r.tiene("km")) " · " + milesKm(r.i("km")) else ""
                        Subtexto(fmtFecha(LocalDate.parse(r.s("fecha"))) + ppl + kmTxt)
                    }
                    Boton("Borrar", { borrar = r.l("id") }, tipo = 2)
                }
            }
        }
    }

    Confirmar(borrar != null, "Borrar recarga", "Se borra la recarga y también el gasto de nafta que se creó con ella. ¿Borrar?",
        onOk = {
            val id = borrar
            if (id != null) {
                val fila = db.one("SELECT gasto_id FROM combustible WHERE id=?", id)
                if (fila != null && fila.tiene("gasto_id")) db.exec("DELETE FROM gastos WHERE id=?", fila.l("gasto_id"))
                db.exec("DELETE FROM combustible WHERE id=?", id)
            }
            borrar = null
            app.cambio("Recarga borrada")
        },
        onCancel = { borrar = null })
}
