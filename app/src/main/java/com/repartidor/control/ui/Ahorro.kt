package com.repartidor.control.ui

import androidx.compose.ui.graphics.Color
import com.repartidor.control.AhorroHoy
import com.repartidor.control.money
import com.repartidor.control.money0

/** (texto, color, texto del botón o null) para mostrar si hoy apartaste la plata de tu meta. */
fun textoAhorro(ea: AhorroHoy?, mutedColor: Color): Triple<String, Color, String?> {
    if (ea == null) {
        return Triple("Definí tu meta y cargá turnos para saber cuánto apartar cada día.", mutedColor, null)
    }
    val boton = "Ya aparté " + money0(ea.falta)
    return when (ea.estado) {
        "cumplido" -> Triple("✔ Hoy apartaste ${money(ea.hecho)} para «${ea.meta}». ¡Objetivo del día cumplido!", Paleta.Verde, null)
        "parcial" -> Triple("◐ Apartaste ${money(ea.hecho)} de ${money(ea.objetivo)} hoy. Faltan ${money(ea.falta)}.", Paleta.Ambar, boton)
        "pendiente" -> Triple("⚠ Todavía no apartaste la plata de hoy para «${ea.meta}»: ${money(ea.objetivo)}.", Paleta.Rojo, boton)
        else -> Triple("Hoy todavía no cargaste turno. Cuando trabajes, te toca apartar ${money(ea.objetivo)} para «${ea.meta}».", mutedColor, boton)
    }
}
