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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.fmtCorto
import com.repartidor.control.fmtFecha
import com.repartidor.control.money
import com.repartidor.control.num
import com.repartidor.control.periodoSemana
import java.time.LocalDate

@Composable
fun DescuentosScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version
    val hoy = LocalDate.now()

    var segMonto by remember { mutableStateOf("") }
    var segDesde by remember { mutableStateOf(hoy) }
    var crTotal by remember { mutableStateOf("") }
    var crCuota by remember { mutableStateOf("") }
    var crDesde by remember { mutableStateOf(hoy) }
    var borrar by remember { mutableStateOf<Long?>(null) }
    var finalizar by remember { mutableStateOf<Long?>(null) }

    val reglas = remember(v) { c.descuentos() }
    val finSemana = periodoSemana(hoy, db.diaCierre).second
    val estaSemana = remember(v) { c.descuentoSemana(finSemana) }
    val estados = remember(v) {
        reglas.filter { it.tipo == "credito" }.associate { it.id to c.creditoEstado(it, hoy) }
    }

    fun agregarSeguro() {
        val m = num(segMonto)
        if (m == null || m <= 0) { app.aviso = "Ingresá el monto semanal del seguro"; return }
        db.exec("INSERT INTO descuentos(tipo,monto,desde,total,nota) VALUES(?,?,?,?,?)", "seguro", m, segDesde.toString(), 0.0, "")
        segMonto = ""
        app.cambio("Seguro de la app agregado: ${money(m)} por semana")
    }

    fun agregarCredito() {
        val t = num(crTotal)
        val cu = num(crCuota)
        if (t == null || t <= 0) { app.aviso = "Ingresá el monto del crédito"; return }
        if (cu == null || cu <= 0) { app.aviso = "Ingresá la cuota semanal"; return }
        if (cu > t) { app.aviso = "La cuota no puede ser mayor que el crédito"; return }
        db.exec("INSERT INTO descuentos(tipo,monto,desde,total,nota) VALUES(?,?,?,?,?)", "credito", cu, crDesde.toString(), t, "")
        crTotal = ""; crCuota = ""
        app.cambio("Crédito agregado: cuota de ${money(cu)} por semana")
    }

    Pantalla {
        Titulo("Seguro y crédito de la app", "Lo que la plataforma (Rappi, PedidosYa, etc.) te descuenta cada semana")
        Subtexto("Ojo: el seguro de acá es el que cobra la app de reparto. No es el seguro de tu vehículo (ese se carga en Recordatorios > Vencimientos).")

        FilaKpis {
            KpiMonto("Seguro de la app esta semana", estaSemana.seguro, Paleta.Ambar, peso())
            KpiMonto("Cuota del crédito", estaSemana.credito, Paleta.Violeta, peso())
        }
        FilaKpis {
            KpiMonto("Total a descontar", estaSemana.total, Paleta.Rojo, peso())
        }
        Subtexto("Semana actual: hasta el ${fmtFecha(finSemana)}. Aparece restado en Inicio, Resumen, calendario de cobros, meta y punto de equilibrio.")
        Subtexto("Si lo que hacés en la semana no alcanza para cubrir el seguro y la cuota, la diferencia queda como deuda con la app y pasa a la semana siguiente, donde se va descontando con lo que hagas.")

        Panel("Seguro de la app") {
            Subtexto("Lo que la app te cobra por semana por su seguro (no es el seguro del vehículo). Si el monto cambia, finalizá el anterior y agregá uno nuevo desde la semana que corresponda.")
            CampoMonto("Monto semanal", segMonto, { segMonto = it })
            Subtexto("Se descuenta desde la semana de esta fecha:")
            CampoFecha(segDesde, { segDesde = it })
            Boton("Agregar seguro de la app", { agregarSeguro() })
        }

        Panel("Crédito que te da la app") {
            Subtexto("Cargá el monto del crédito y la cuota semanal. La app descuenta una cuota por semana hasta terminarlo.")
            Dos {
                CampoMonto("Monto del crédito", crTotal, { crTotal = it }, peso())
                CampoMonto("Cuota semanal", crCuota, { crCuota = it }, peso())
            }
            val t = num(crTotal)
            val cu = num(crCuota)
            if (t != null && cu != null && t > 0 && cu > 0 && cu <= t) {
                Subtexto("Son ${Math.ceil(t / cu).toInt()} cuotas semanales.")
            }
            Subtexto("Empieza a descontarse desde la semana de esta fecha:")
            CampoFecha(crDesde, { crDesde = it })
            Boton("Agregar crédito", { agregarCredito() })
        }

        Panel("Tus descuentos") {
            if (reglas.isEmpty()) Subtexto("Todavía no cargaste ningún seguro de la app ni crédito.")
            for (r in reglas) {
                val activa = r.hasta == null || !r.hasta.isBefore(hoy)
                val e = estados[r.id]
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (r.tipo == "credito") {
                        Text("Crédito de ${money(r.total)} · cuota ${money(r.monto)} por semana", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (e != null) {
                            Subtexto("Pagado ${money(e.pagado)} · pendiente ${money(e.pendiente)} (${e.cuotasRestantes} cuotas)")
                            if (e.pendiente <= 0.005) Text("✔ Crédito terminado de pagar", color = Paleta.Verde, fontSize = 13.sp)
                        }
                    } else {
                        Text("Seguro de la app · ${money(r.monto)} por semana", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    val hasta = r.hasta
                    Subtexto(
                        "Desde ${fmtFecha(r.desde)}" +
                            (if (hasta != null) " · finalizado el ${fmtCorto(hasta)}" else if (activa) " · activo" else "")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (activa && r.hasta == null) Boton("Finalizar", { finalizar = r.id }, tipo = 1)
                        Boton("Borrar", { borrar = r.id }, tipo = 2)
                    }
                }
            }
        }
    }

    Confirmar(finalizar != null, "Finalizar descuento",
        "Deja de descontarse desde la semana próxima. Lo que ya se descontó queda en el historial.",
        onOk = {
            finalizar?.let { db.exec("UPDATE descuentos SET hasta=? WHERE id=?", hoy.toString(), it) }
            finalizar = null
            app.cambio("Descuento finalizado")
        },
        onCancel = { finalizar = null })
    Confirmar(borrar != null, "Borrar descuento",
        "Se borra la regla completa, también de las semanas pasadas: cambia lo que figuraba como cobrado. ¿Borrar?",
        onOk = {
            borrar?.let { db.exec("DELETE FROM descuentos WHERE id=?", it) }
            borrar = null
            app.cambio("Descuento borrado")
        },
        onCancel = { borrar = null })
}
