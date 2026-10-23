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
import java.time.LocalDate
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
        aplicarAutomaticos()
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
        aplicarAutomaticos()
        calc.invalidar()
        version++
        if (mensaje != null) aviso = mensaje
        programarRespaldo()
    }

    // ---------------------------------------------------------------- descuento automático del seguro y el crédito
    /**
     * Si el descuento automático está activo, carga el seguro y la cuota del crédito en cada día de la semana
     * elegido que ya pasó desde la última revisión. Lo que el usuario borre a mano no se vuelve a crear.
     * Devuelve cuántos descuentos cargó.
     */
    @Synchronized
    fun aplicarAutomaticos(hoy: LocalDate = LocalDate.now()): Int {
        if (db.cfgI("auto_activo", 0) == 0) return 0
        val seguro = db.cfgD("auto_seguro", 0.0)
        val cuota = db.cfgD("auto_credito", 0.0)
        if (seguro <= 0 && cuota <= 0) return 0
        val ultima = parseFecha(db.cfgS("auto_hasta", "")) ?: return 0
        val totalCredito = db.cfgD("credito_total", 0.0)
        var f = ultima.plusDays(1)
        if (f.isBefore(hoy.minusDays(400))) f = hoy.minusDays(400)
        var cargados = 0
        while (!f.isAfter(hoy)) {
            if (diaSemana(f) == db.diaDescuento) {
                val fecha = f.toString()
                if (seguro > 0 && db.one("SELECT 1 x FROM cargos_app WHERE fecha=? AND tipo='seguro'", fecha) == null) {
                    db.exec("INSERT INTO cargos_app(fecha,tipo,monto,nota) VALUES(?,?,?,?)", fecha, "seguro", seguro, "auto")
                    cargados++
                }
                if (cuota > 0 && db.one("SELECT 1 x FROM cargos_app WHERE fecha=? AND tipo='credito'", fecha) == null) {
                    var monto = cuota
                    if (totalCredito > 0) {
                        val pagado = db.one("SELECT COALESCE(SUM(monto),0) m FROM cargos_app WHERE tipo='credito'")!!.d("m")
                        monto = minOf(cuota, totalCredito - pagado)
                    }
                    if (monto > 0.005) {
                        db.exec("INSERT INTO cargos_app(fecha,tipo,monto,nota) VALUES(?,?,?,?)", fecha, "credito", monto, "auto")
                        cargados++
                    }
                }
            }
            f = f.plusDays(1)
        }
        db.setCfg("auto_hasta", hoy.toString())
        return cargados
    }

    /** Se llama al abrir la app: si pasó el día del descuento mientras estaba cerrada, lo carga. */
    fun revisarAutomaticos() {
        if (aplicarAutomaticos() > 0) {
            calc.invalidar()
            version++
            programarRespaldo()
        }
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
