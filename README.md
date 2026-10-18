# Control Rider

App de Android (Kotlin + Jetpack Compose) para llevar el control de ganancias de un repartidor:
saldo diario, semanal y mensual, efectivo recaudado, bono, propinas, gastos, gastos fijos del hogar,
mantenimiento del vehículo (con km), recordatorios, rendimiento por hora y metas de ahorro.

## Descargar el APK
Cada vez que se sube código a GitHub, **GitHub compila el APK solo** (tarda unos 5 a 10 minutos).

- Link directo, siempre a la última versión:
  `https://github.com/TU_USUARIO/TU_REPOSITORIO/releases/latest/download/ControlRider.apk`
- O entrá a la pestaña **Releases** del repositorio y bajá `ControlRider.apk`.

### Instalarlo en el teléfono
1. Abrí el link desde el teléfono y descargá `ControlRider.apk`.
2. Android te va a pedir permiso para *instalar apps desconocidas* con el navegador que usaste: aceptalo.
3. Si aparece el aviso de Google Play Protect ("app no verificada"), elegí *Instalar de todos modos*:
   la app no está en Play Store, la firmó este repositorio.
4. Para actualizar, bajá el APK nuevo e instalalo encima: se conservan tus datos.

## Compilar a mano
- En GitHub: pestaña **Actions** > *Compilar APK de Control Rider* > **Run workflow**.
- En tu compu: abrí la carpeta con Android Studio y tocá Run ▶ (necesita Android 8.0 o superior, JDK 17).

## Si la compilación falla
Entrá a **Actions**, abrí la corrida con la cruz roja y copiá el error del paso *Compilar el APK*
(o bajá el archivo `registro-de-compilacion`). Con ese texto se corrige el código.

## Cálculo
`Saldo = base + adicionales + adicional por grupo + bono − efectivo recaudado`.
Positivo = a cobrar; negativo = se lo debés a la app. Las propinas van aparte (no se suman ni se restan).

## Datos
Se guardan en una base SQLite dentro del teléfono. La copia de seguridad de Google los respalda si está activada.
Desde *Más > Ajustes y datos* se exporta todo a CSV.

## Nota sobre la firma
El APK se firma con la clave `app/controlrider.keystore` (incluida en el repositorio) para que las
actualizaciones se instalen sobre la versión anterior. Es una clave pensada para uso personal:
si el repositorio es público, cualquiera puede ver esa clave. Si querés más seguridad, hacé el repositorio
privado o generá tu propia clave.
