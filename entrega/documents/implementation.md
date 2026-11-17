# Plan de implementación y trazabilidad

## Objetivo inmediato

Cerrar RF1: ofrecer un botón visible **“Definir / Cambiar contraseña”** en el catálogo; aceptar una clave nueva al definirla y exigir clave anterior al cambiarla; validar longitud y alfanumérico; guardar únicamente hash con sal en el archivo local existente. Mantener la caducidad de sesión de 10 minutos y conservar acceso a instalaciones existentes mediante migración de `pin.conf` en claro.

## Diseño por capas

### Capa 4 — `com.monkey.model`

Sin cambios para RF1. La credencial no pertenece a `cita`, `servicios` o `venta` ni requiere columna SQL. No alterar el esquema ER.

### Capa 3 — `com.monkey.data_persistance`

- Añadir `RepositorioPin` para leer y escribir `MonkeyElegantSystem/pin.conf` localmente, fuera de SQLite.
- Escritura UTF-8 reemplazando el archivo de forma segura; validar que solo se persista la representación PBKDF2 versionada, nunca la clave recibida.
- Lectura distingue: archivo ausente (inicializar con clave inicial PRD), hash versionado (usar), valor legado en claro (migrar inmediatamente a hash), contenido corrupto (detener con error explícito; no reemplazarlo silenciosamente).
- Pruebas nuevas: ausencia, lectura/escritura de hash, migración heredada, no guardar texto plano y errores de E/S.

### Capa 2 — `com.monkey.logic.SesionAdmin`

- Mantener el constructor existente `SesionAdmin(String)` por compatibilidad de pruebas/consumidores y agregar la variante con `RepositorioPin`.
- Validar claves nuevas: de 6 a 12 caracteres ASCII alfanuméricos.
- Hashear con `PBKDF2WithHmacSHA256`, sal aleatoria por credencial y coste versionado; comparar en tiempo constante con `MessageDigest.isEqual`.
- Agregar `cambiarPin(pinAnterior, pinNuevo)`: rechaza clave anterior incorrecta, valida clave nueva, guarda primero el hash y solo después sustituye el hash de sesión. Reinicia/renueva la sesión después de una actualización exitosa.
- Migrar el valor heredado en claro durante la construcción cuando exista repositorio. No permitir bajar el hash a texto claro.
- Pruebas: PIN correcto/incorrecto, límites 5/6/12/13, símbolos/espacios, cambio que acepta la nueva y rechaza la anterior, persistencia tras reinicio, migración, expiración y renovación de sesión.

### Capa 1 — `com.monkey.ui`

- `VistaCatalogo`: botón siempre visible “Definir / Cambiar contraseña” y listener protegido por `try/finally`. No pide autorización genérica antes del diálogo: el flujo exige siempre la clave anterior para cambiarla.
- `DialogoPinAdmin`: añadir diálogo de cambio con tres campos `JPasswordField` (anterior, nueva, confirmar); comparación de confirmación en UI, reglas de clave en `SesionAdmin`, mensajes claros y campos limpiados al cerrar. El PRD inicializa una clave inicial, por lo que el botón pide esa clave anterior incluso en una instalación nueva; después permite reemplazarla por la que defina el administrador.
- Mantener el diálogo de acceso actual y la sesión pasiva de 10 minutos.
- Pruebas UI: botón visible/identificable, todos los campos ocultos, cancelación sin cambios, confirmación distinta denegada. Los tests de lógica y persistencia verifican las ramas de éxito/error sin depender de automatización de diálogo modal.

### Arranque — `com.monkey.App`

- Crear el directorio local y ensamblar `RepositorioPin` y `SesionAdmin` dentro del arranque, sin introducir base de datos de credenciales.
- Conservar `PIN_INICIAL` para la primera instalación y migrar el archivo heredado automáticamente.
- Actualizar pruebas de inicialización para afirmar que el archivo contiene un hash que no revela el PIN y que la credencial inicial autentica.

## Archivos que requieren cambio

| Archivo | Cambio |
|---|---|
| `src/main/java/com/monkey/App.java` | Ensamblar repositorio de PIN y sesión con almacenamiento persistente. |
| `src/main/java/com/monkey/data_persistance/RepositorioPin.java` | Nuevo almacenamiento local seguro, separado de SQLite. |
| `src/main/java/com/monkey/logic/SesionAdmin.java` | Hash, validación robusta, cambio autenticado y persistencia delegada. |
| `src/main/java/com/monkey/ui/DialogoPinAdmin.java` | Flujo modal de cambio; conservar acceso de una sola clave. |
| `src/main/java/com/monkey/ui/VistaCatalogo.java` | Botón visible para abrir cambio de contraseña. |
| `src/test/java/com/monkey/AppTest.java` | Actualizar pruebas del archivo/hash inicial. |
| `src/test/java/com/monkey/data_persistance/RepositorioPinTest.java` | Nuevo conjunto unitario de almacenamiento/migración. |
| `src/test/java/com/monkey/logic/SesionAdminTest.java` | Añadir reglas, cambio, persistencia y migración. |
| `src/test/java/com/monkey/ui/VistaCatalogoTest.java` y `DialogoPinAdminTest.java` | Añadir comprobaciones estructurales del control y campos ocultos. |

No requieren cambio: `conexion_DB`, repositorios de agenda/ventas/servicios, `Venta`, el esquema SQLite y `ER.png`.

## Requisito de hora de término (RF9)

El modelo previo solo guardaba la hora de inicio. Para conservar el flujo actual de selección por bloques horarios, cada cita tiene duración fija de 60 minutos: `horaFin = hora + 1 hora`. El término se persiste en `citas.hora_fin`, se presenta en la agenda y en la confirmación de registro, y se carga desde las búsquedas y el POS. La inicialización de SQLite agrega esta columna a bases previas y completa sus filas con el mismo cálculo. El índice de disponibilidad sigue usando fecha y hora de inicio, por lo que no se modifica la regla de reserva.

## Diagrama y documentación

- `diagram.puml` actualiza la clase de sesión, agrega `RepositorioPin` y sus dependencias, y muestra el botón de catálogo. No agregar tabla a `ER.png` porque el hash continúa en archivo local.
- Para exportar la vista PNG al instalar PlantUML, ejecutar desde `entrega/documents`: `plantuml -tpng diagram.puml`. El entorno de esta revisión no tiene PlantUML instalado; por eso el `.puml` actualizado es el diagrama corregido entregado y `diagram.png` queda identificado como exportación anterior.
- `specs.md` es la lista de requisitos PRD y brechas verificadas.
- `AGENTS.md` conserva nombres reales (`data_persistance`, modelos en minúscula) y reglas de trabajo; el PRD prevalece ante conflictos.

## Migración, despliegue y verificación

1. Respaldo lógico de `pin.conf` existente sin imprimir su contenido en logs.
2. En primer arranque posterior, transformar texto plano heredado en hash versionado; probar login con la clave anterior.
3. Cambiar desde Catálogo y reiniciar; la clave nueva debe autenticar y la antigua debe ser rechazada.
4. Ejecutar compilación limpia, todas las pruebas JUnit, y verificación visual de UI Swing en entorno gráfico.
5. Probar archivo ausente, archivo legado, hash válido/corrupto, I/O fallido y archivo que no pueda escribirse. Ante fallo, no sustituir credencial existente.

## Riesgos y decisiones

- El PRD exige compatibilidad de la credencial inicial preconfigurada. Mientras no se cambie, sigue siendo conocida; la UI debe facilitar el cambio desde el catálogo. Sustituirla por configuración inicial interactiva sería un cambio adicional de requisito.
- `pin.conf` es un archivo local. Las ACL del usuario de Windows protegen el directorio; no se requiere una dependencia criptográfica externa.
- PBKDF2 es parte de Java 17. El formato guardado incluye algoritmo/versión, iteraciones, sal y derivado para permitir actualización futura.
