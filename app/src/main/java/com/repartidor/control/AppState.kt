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
import java.io.File
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

    init {
        restaurarInternaSiVacio()
    }

    // ---------------------------------------------------------------- copia interna (la recupera Android al reinstalar)
    private val archivoInterno: File get() = File(appContext.filesDir, ARCHIVO_INTERNO)

    private fun hayDatos(): Boolean =
        db.one("SELECT COUNT(*) c FROM diario")!!.i("c") > 0 ||
            db.one("SELECT COUNT(*) c FROM gastos")!!.i("c") > 0 ||
            db.one("SELECT 1 x FROM meta WHERE id=1") != null

    /**
     * Guarda un archivo con todos los datos dentro de la app. Android lo incluye en su copia automática
     * a la cuenta de Google, así que al reinstalar vuelve solo (ver res/xml/backup_rules.xml).
     */
    @Synchronized
    private fun guardarCopiaInterna() {
        try {
            if (!hayDatos()) return
            val tmp = File(appContext.filesDir, "$ARCHIVO_INTERNO.tmp")
            tmp.writeText(Respaldo.exportar(db), Charsets.UTF_8)
            if (!tmp.renameTo(archivoInterno)) {
                archivoInterno.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            // la copia interna es un extra: si falla no se interrumpe nada
        }
    }

    /** Si la app arranca vacía pero Android devolvió la copia interna (reinstalación), la carga. */
    private fun restaurarInternaSiVacio() {
        try {
            val f = archivoInterno
            if (f.exists() && !hayDatos()) Respaldo.importar(db, f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            // archivo dañado o de otra app: se ignora
        }
    }

    /**
     * Restaurar en un solo paso: el usuario elige la carpeta donde estaba la copia automática,
     * se carga el archivo de esa carpeta y se vuelve a activar la copia automática en ella.
     */
    fun restaurarDesdeCarpeta(uri: Uri): String {
        try {
            val arbol = DocumentFile.fromTreeUri(appContext, uri) ?: return "No se pudo abrir esa carpeta."
            val archivo = arbol.findFile(Respaldo.NOMBRE) ?: return "En esa carpeta no está el archivo ${Respaldo.NOMBRE}."
            val texto = appContext.contentResolver.openInputStream(archivo.uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: return "No se pudo leer el archivo de la copia."
            val n = Respaldo.importar(db, texto)
            db.setCfg("respaldo_carpeta", uri.toString())
            cambio("Datos recuperados: $n registros ✔ La copia automática quedó activada")
            return "ok"
        } catch (e: Exception) {
            return "No se pudo restaurar: ${e.message ?: e.javaClass.simpleName}"
        }
    }

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
        manejador.removeCallbacks(tareaRespaldo)
        manejador.postDelayed(tareaRespaldo, 4000)
    }

    /** Guarda la copia en segundo plano (no traba la pantalla aunque la carpeta sea de Google Drive). */
    fun respaldarAsync() {
        Thread {
            guardarCopiaInterna()
            if (respaldoActivo) respaldarAhora()
        }.start()
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

private const val ARCHIVO_INTERNO = "respaldo-interno.json"
