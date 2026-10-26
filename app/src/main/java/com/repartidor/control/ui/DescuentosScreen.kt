package com.repartidor.control.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.sp
import com.repartidor.control.AppState
import com.repartidor.control.DIAS
import com.repartidor.control.finDeMes
import com.repartidor.control.fmtFecha
import com.repartidor.control.fmtMonto
import com.repartidor.control.money
import com.repartidor.control.num
import java.time.LocalDate

/** Configuración del descuento automático: el día de la semana en que la app descuenta y los montos semanales. */
@Composable
fun AutomaticoPanel(app: AppState) {
    val db = app.db
    var activo by remember { mutableStateOf(db.cfgI("auto_activo", 0) == 1) }
    var dia by remember { mutableStateOf(db.diaDescuento) }
    var seg by remember { mutableStateOf(db.cfgD("auto_seguro", 0.0).let { if (it > 0) fmtMonto(it) else "" }) }
    var cre by remember { mutableStateOf(db.cfgD("auto_credito", 0.0).let { if (it > 0) fmtMonto(it) else "" }) }

    fun guardar() {
        val s = num(seg)
        val c = num(cre)
        if (s == null || c == null) { app.aviso = "Revisá los montos: deben ser números positivos"; return }
        if (activo && s <= 0 && c <= 0) { app.aviso = "Cargá el monto del seguro o de la cuota del crédito"; return }
        val estabaActivo = db.cfgI("auto_activo", 0) == 1
        db.setCfg("auto_seguro", s)
        db.setCfg("auto_credito", c)
        db.setCfg("auto_dia", dia)
        db.setCfg("auto_activo", if (activo) 1 else 0)
        if (activo && !estabaActivo) db.setCfg("auto_hasta", LocalDate.now().minusDays(1).toString())
        app.cambio(if (activo) "Descuento automático activado: cada ${DIAS[dia].lowercase()}" else "Descuento automático desactivado")
    }

    Panel("Descuento automático", acento = Paleta.Ambar) {
        Subtexto("Elegí el día de la semana en que la app te descuenta el seguro y el crédito, y los montos. La app los carga sola ese día, todas las semanas. Si un monto cambia, actualizalo acá: vale desde el próximo descuento.")
        Selector("Día que te descuentan", DIAS, DIAS[dia], { dia = DIAS.indexOf(it) })
        Dos {
            CampoMonto("Seguro semanal", seg, { seg = it }, peso())
            CampoMonto("Cuota semanal del crédito", cre, { cre = it }, peso())
        }
        Boton(if (activo) "Automático: ACTIVADO (tocá para desactivar)" else "Automático: desactivado (tocá para activar)",
            { activo = !activo }, Modifier.fillMaxWidth(), 1)
        Boton("Guardar", { guardar() }, Modifier.fillMaxWidth())
        Subtexto("Si necesitás corregir un día, borralo en el historial o cargalo a mano en Diario: lo que borres no se vuelve a crear. Si el crédito tiene monto total, la última cuota se ajusta y después deja de descontarse.")
    }
}

/** Más > Seguro y crédito de la app: resumen e historial. Los descuentos se cargan por día en la solapa Diario. */
@Composable
fun DescuentosScreen(app: AppState) {
    val db = app.db
    val c = app.calc
    val v = app.version
    val hoy = LocalDate.now()

    var credTotal by remember { mutableStateOf(db.cfgD("credito_total", 0.0).let { if (it > 0) fmtMonto(it) else "" }) }
    var borrar by remember { mutableStateOf<Long?>(null) }

    val mes = remember(v) { c.descuentosPeriodo(hoy.withDayOfMonth(1), finDeMes(hoy)) }
    val tipico = remember(v) { c.descuentoEstimado(hoy) }
    val pagado = remember(v) { c.creditoPagado() }
    val total = db.cfgD("credito_total", 0.0)
    val historial = remember(v) { db.rows("SELECT * FROM cargos_app ORDER BY fecha DESC, id DESC LIMIT 60") }

    Pantalla {
        Titulo("Seguro y crédito de la app", "Lo que la plataforma te descuenta, cargado por día")
        Subtexto("Se cargan en la solapa Diario, en el día que la app los descuenta. Ese monto se resta del saldo de la semana en que se carga. El seguro de acá es el de la app de reparto, no el de tu vehículo.")

        FilaKpis {
            KpiMonto("Seguro este mes", mes.seguro, Paleta.Ambar, peso())
            KpiMonto("Crédito este mes", mes.credito, Paleta.Violeta, peso())
        }
        FilaKpis {
            KpiMonto("Descuento típico por semana", tipico.total, Paleta.Rojo, peso())
        }

        AutomaticoPanel(app)

        Panel("Crédito que te da la app", acento = Paleta.Violeta) {
            CampoMonto("Monto total del crédito (opcional)", credTotal, { credTotal = it })
            Boton("Guardar monto", {
                val t = num(credTotal)
                if (t == null) app.aviso = "El monto debe ser un número positivo"
                else { db.setCfg("credito_total", t); app.cambio("Monto del crédito guardado") }
            }, tipo = 1)
            FilaDato("Pagado hasta ahora", money(pagado), Paleta.Violeta)
            if (total > 0) {
                val pend = maxOf(0.0, total - pagado)
                FilaDato("Falta pagar", money(pend), if (pend <= 0.005) Paleta.Verde else Paleta.Rojo, true)
                if (pend <= 0.005) Text("✔ Crédito terminado de pagar", color = Paleta.Verde, fontSize = 13.sp)
                Barra((pagado / total).toFloat(), Paleta.Violeta)
            }
        }

        Panel("Historial de descuentos") {
            if (historial.isEmpty()) Subtexto("Todavía no cargaste ningún descuento. Hacelo en la solapa Diario.")
            for (r in historial) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${fmtFecha(LocalDate.parse(r.s("fecha")))} · ${if (r.s("tipo") == "credito") "Crédito" else "Seguro"} · ${money(r.d("monto"))}" +
                            (if (r.s("nota") == "auto") " · auto" else ""),
                        fontSize = 14.sp
                    )
                    Boton("Borrar", { borrar = r.l("id") }, tipo = 2)
                }
            }
        }
    }

    Confirmar(borrar != null, "Borrar descuento", "¿Borrar este descuento? Cambia el saldo de esa semana.",
        onOk = {
            borrar?.let { db.exec("DELETE FROM cargos_app WHERE id=?", it) }
            borrar = null
            app.cambio("Descuento borrado")
        },
        onCancel = { borrar = null })
}
