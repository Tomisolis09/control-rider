package com.repartidor.control

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Copia de seguridad en un archivo de texto (JSON) con todos tus datos. */
object Respaldo {
    const val NOMBRE = "ControlRider-respaldo.json"

    private val TABLAS = listOf(
        "diario", "gastos", "gastos_fijos", "mantenimiento", "items_custom", "meta", "config",
        "recordatorios", "vencimientos", "aportes", "descuentos", "combustible", "cargos_app"
    )

    /** Ajustes que dependen de este teléfono y no se guardan en la copia. */
    private val EXCLUIDAS = setOf("respaldo_carpeta", "respaldo_ultimo")

    fun exportar(db: Db): String {
        val raiz = JSONObject()
        raiz.put("app", "ControlRider")
        raiz.put("version", 2)
        raiz.put("fecha", LocalDateTime.now().toString())
        val tablas = JSONObject()
        for (t in TABLAS) {
            val arreglo = JSONArray()
            for (fila in db.volcar(t)) {
                if (t == "config" && fila["clave"] in EXCLUIDAS) continue
                val o = JSONObject()
                for ((k, v) in fila) o.put(k, v ?: JSONObject.NULL)
                arreglo.put(o)
            }
            tablas.put(t, arreglo)
        }
        raiz.put("tablas", tablas)
        return raiz.toString()
    }

    /** Reemplaza todos los datos por los de la copia. Devuelve cuántos registros se restauraron. */
    fun importar(db: Db, texto: String): Int {
        val raiz = JSONObject(texto)
        if (raiz.optString("app") != "ControlRider") throw IllegalArgumentException("No es una copia de Control Rider")
        val tablas = raiz.getJSONObject("tablas")
        val datos = LinkedHashMap<String, List<Map<String, Any?>>>()
        var total = 0
        for (t in TABLAS) {
            if (!tablas.has(t)) continue
            val arreglo = tablas.getJSONArray(t)
            val filas = ArrayList<Map<String, Any?>>()
            for (i in 0 until arreglo.length()) {
                val o = arreglo.getJSONObject(i)
                val m = LinkedHashMap<String, Any?>()
                val claves = o.keys()
                while (claves.hasNext()) {
                    val k = claves.next()
                    val v = o.get(k)
                    m[k] = if (v == JSONObject.NULL) null else v
                }
                if (t == "config" && m["clave"] in EXCLUIDAS) continue
                filas.add(m)
            }
            datos[t] = filas
            total += filas.size
        }
        // la carpeta de copia automática elegida en este teléfono se conserva
        val carpeta = db.cfgS("respaldo_carpeta", "")
        db.reemplazar(datos)
        if (carpeta.isNotEmpty()) db.setCfg("respaldo_carpeta", carpeta)
        return total
    }
}
