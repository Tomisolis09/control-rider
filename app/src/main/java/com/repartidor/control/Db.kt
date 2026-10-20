package com.repartidor.control

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Una fila de resultados con acceso tipado por nombre de columna. */
class Row(private val m: Map<String, Any?>) {
    fun s(k: String): String = m[k]?.toString() ?: ""
    fun sn(k: String): String? = m[k]?.toString()
    fun d(k: String): Double = (m[k] as? Number)?.toDouble() ?: 0.0
    fun i(k: String): Int = (m[k] as? Number)?.toInt() ?: 0
    fun l(k: String): Long = (m[k] as? Number)?.toLong() ?: 0L
    fun tiene(k: String): Boolean = m[k] != null
}

/** Tablas agregadas en la versión 2 de la base (descuentos semanales y combustible). */
private val ESQUEMA_V2 = listOf(
    """CREATE TABLE descuentos(
        id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, monto REAL DEFAULT 0, desde TEXT NOT NULL,
        hasta TEXT, total REAL DEFAULT 0, nota TEXT)""",
    """CREATE TABLE combustible(
        id INTEGER PRIMARY KEY AUTOINCREMENT, fecha TEXT NOT NULL, vehiculo TEXT, litros REAL DEFAULT 0,
        monto REAL DEFAULT 0, km INTEGER, gasto_id INTEGER)"""
)

private val ESQUEMA = listOf(
    """CREATE TABLE diario(
        id INTEGER PRIMARY KEY AUTOINCREMENT, fecha TEXT NOT NULL, app TEXT,
        base REAL DEFAULT 0, adicionales REAL DEFAULT 0, grupo REAL DEFAULT 0, propinas REAL DEFAULT 0,
        efectivo REAL DEFAULT 0, pedidos INTEGER DEFAULT 0, hora_inicio TEXT, hora_fin TEXT, bono REAL DEFAULT 0)""",
    """CREATE TABLE gastos(
        id INTEGER PRIMARY KEY AUTOINCREMENT, fecha TEXT NOT NULL, categoria TEXT, monto REAL DEFAULT 0, nota TEXT)""",
    """CREATE TABLE gastos_fijos(
        id INTEGER PRIMARY KEY AUTOINCREMENT, fecha TEXT NOT NULL, categoria TEXT, monto REAL DEFAULT 0, nota TEXT)""",
    """CREATE TABLE mantenimiento(
        id INTEGER PRIMARY KEY AUTOINCREMENT, fecha TEXT NOT NULL, vehiculo TEXT, item TEXT,
        costo REAL DEFAULT 0, nota TEXT, km INTEGER)""",
    """CREATE TABLE items_custom(
        id INTEGER PRIMARY KEY AUTOINCREMENT, vehiculo TEXT, item TEXT, UNIQUE(vehiculo, item))""",
    """CREATE TABLE meta(
        id INTEGER PRIMARY KEY CHECK (id = 1), nombre TEXT, objetivo REAL, ahorrado REAL, fecha_limite TEXT,
        dias_semana INTEGER, incluir_propinas INTEGER, fecha_inicio TEXT)""",
    """CREATE TABLE config(clave TEXT PRIMARY KEY, valor TEXT)""",
    """CREATE TABLE recordatorios(
        id INTEGER PRIMARY KEY AUTOINCREMENT, vehiculo TEXT, item TEXT, cada_dias INTEGER DEFAULT 0,
        cada_pedidos INTEGER DEFAULT 0, cada_km INTEGER DEFAULT 0, desde TEXT, UNIQUE(vehiculo, item))""",
    """CREATE TABLE vencimientos(
        id INTEGER PRIMARY KEY AUTOINCREMENT, concepto TEXT, fecha TEXT NOT NULL, nota TEXT)""",
    """CREATE TABLE aportes(
        id INTEGER PRIMARY KEY AUTOINCREMENT, fecha TEXT NOT NULL, monto REAL NOT NULL, nota TEXT)""",
    "CREATE INDEX ix_diario_fecha ON diario(fecha)",
    "CREATE INDEX ix_gastos_fecha ON gastos(fecha)"
) + ESQUEMA_V2

class Db(context: Context) : SQLiteOpenHelper(context, "repartidor.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        for (sql in ESQUEMA) db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            for (sql in ESQUEMA_V2) db.execSQL(sql)
        }
    }

    fun rows(sql: String, vararg args: Any?): List<Row> {
        val params = Array(args.size) { args[it].toString() }
        val out = ArrayList<Row>()
        readableDatabase.rawQuery(sql, params).use { c ->
            while (c.moveToNext()) {
                val m = HashMap<String, Any?>()
                for (i in 0 until c.columnCount) {
                    m[c.getColumnName(i)] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_NULL -> null
                        else -> c.getString(i)
                    }
                }
                out.add(Row(m))
            }
        }
        return out
    }

    fun one(sql: String, vararg args: Any?): Row? = rows(sql, *args).firstOrNull()

    fun exec(sql: String, vararg args: Any?) {
        val params = arrayOfNulls<Any?>(args.size)
        for (i in args.indices) params[i] = args[i]
        writableDatabase.execSQL(sql, params)
    }

    /** Inserta una fila y devuelve su id. */
    fun insertar(sql: String, vararg args: Any?): Long {
        val st = writableDatabase.compileStatement(sql)
        try {
            for (i in args.indices) {
                val a = args[i]
                val pos = i + 1
                when (a) {
                    null -> st.bindNull(pos)
                    is Double -> st.bindDouble(pos, a)
                    is Float -> st.bindDouble(pos, a.toDouble())
                    is Int -> st.bindLong(pos, a.toLong())
                    is Long -> st.bindLong(pos, a)
                    else -> st.bindString(pos, a.toString())
                }
            }
            return st.executeInsert()
        } finally {
            st.close()
        }
    }

    // ---- configuración (clave/valor)
    fun cfgS(k: String, def: String): String = one("SELECT valor FROM config WHERE clave=?", k)?.s("valor") ?: def
    fun cfgI(k: String, def: Int): Int = cfgS(k, def.toString()).toDoubleOrNull()?.toInt() ?: def
    fun cfgD(k: String, def: Double): Double = cfgS(k, def.toString()).toDoubleOrNull() ?: def
    fun setCfg(k: String, v: Any) = exec("INSERT OR REPLACE INTO config(clave, valor) VALUES(?,?)", k, v.toString())

    val diaCierre: Int get() = cfgI("dia_cierre", 0)   // lunes
    val diaCobro: Int get() = cfgI("dia_cobro", 3)     // jueves
}
