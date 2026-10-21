package com.repartidor.control

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Estado global muy simple: cada cambio en la base incrementa `version` y las pantallas se recalculan. */
class AppState(context: Context) {
    val db = Db(context.applicationContext)
    val calc = Calc(db)

    var version by mutableIntStateOf(0)
        private set
    var aviso by mutableStateOf<String?>(null)
    var tema by mutableIntStateOf(db.cfgI("tema", 0))   // 0 = sistema, 1 = claro, 2 = oscuro

    /** Llamar después de cualquier escritura en la base. */
    fun cambio(mensaje: String? = null) {
        calc.invalidar()
        version++
        if (mensaje != null) aviso = mensaje
    }

    fun cambiarTema(t: Int) {
        db.setCfg("tema", t)
        tema = t
    }

    /** Registra de un clic lo que falta apartar hoy para la meta. */
    fun aporteDiario() {
        val hoy = java.time.LocalDate.now()
        val ea = calc.estadoAhorro(hoy)
        if (ea == null || ea.falta <= 0.005) return
        val monto = Math.round(ea.falta * 100) / 100.0
        val hitos = calc.registrarAporte(hoy, monto, "Aporte diario")
        val extra = if (hitos.isNotEmpty()) " · ¡Llegaste al ${hitos.max()}% de tu meta! 🎉" else ""
        cambio("Apartaste ${money(monto)} para tu meta ✔$extra")
    }
}
