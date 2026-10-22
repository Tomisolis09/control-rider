package com.repartidor.control

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Estado global muy simple: cada cambio en la base incrementa `version` y las pantallas se recalculan. */
class AppState(context: Context) {
    private val appContext = context.applicationContext
    val db = Db(appContext)
    val calc = Calc(db)

    var version by mutableIntStateOf(0)
        private set
    var aviso by mutableStateOf<String?>(null)
    var tema by mutableIntStateOf(db.cfgI("tema", 0))   // 0 = sistema, 1 = claro, 2 = oscuro

    /** Estado de la copia automática, para mostrar en Ajustes. */
    var estadoRespaldo by mutableStateOf(db.cfgS("respaldo_ultimo", ""))
        private set
    private val manejador = Handler(Looper.getMainLooper())
    private val tareaRespaldo = Runnable { respaldarAsync() }

    /** Llamar después de cualquier escritura en la base. */
    fun cambio(mensaje: String? = null) {
        calc.invalidar()
        version++
        if (mensaje != null) aviso = mensaje
        programarRespaldo()
    }

    // ---------------------------------------------------------------- copia de seguridad automática
    val respaldoActivo: Boolean get() = db.cfgS("respaldo_carpeta", "").isNotEmpty()

    /** Espera unos segundos después del último cambio y guarda la copia en la carpeta elegida. */
    private fun programarRespaldo() {
        if (!respaldoActivo) return
        manejador.removeCallbacks(tareaRespaldo)
        manejador.postDelayed(tareaRespaldo, 4000)
    }

    /** Guarda la copia en segundo plano (no traba la pantalla aunque la carpeta sea de Google Drive). */
    fun respaldarAsync() {
        if (!respaldoActivo) return
        Thread { respaldarAhora() }.start()
    }

    @Synchronized
    fun respaldarAhora(): Boolean {
        val carpeta = db.cfgS("respaldo_carpeta", "")
        if (carpeta.isEmpty()) return false
        var resultado = "Error: no se pudo guardar la copia. Elegí la carpeta de nuevo."
        var ok = false
        try {
            val arbol = DocumentFile.fromTreeUri(appContext, Uri.parse(carpeta))
            if (arbol != null && arbol.canWrite()) {
                val texto = Respaldo.exportar(db)
                var archivo = arbol.findFile(Respaldo.NOMBRE)
                if (archivo == null) archivo = arbol.createFile("application/json", Respaldo.NOMBRE)
                if (archivo != null) {
                    appContext.contentResolver.openOutputStream(archivo.uri, "wt")?.use {
                        it.write(texto.toByteArray(Charsets.UTF_8))
                    }
                    resultado = "Última copia: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    ok = true
                }
            }
        } catch (e: Exception) {
            resultado = "Error: no se pudo guardar la copia (${e.javaClass.simpleName}). Elegí la carpeta de nuevo."
        }
        db.setCfg("respaldo_ultimo", resultado)
        manejador.post { estadoRespaldo = resultado }
        return ok
    }

    /** Elige la carpeta donde se guarda la copia automática (null la desactiva). */
    fun elegirCarpetaRespaldo(uri: String?) {
        if (uri == null) {
            db.setCfg("respaldo_carpeta", "")
            db.setCfg("respaldo_ultimo", "")
            estadoRespaldo = ""
        } else {
            db.setCfg("respaldo_carpeta", uri)
            respaldarAsync()
        }
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
