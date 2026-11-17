# Instrucciones del proyecto MONKEY BARBER SYSTEM

## Fuente de verdad

- `MONKEY BARBER SYSTEM — Sistema de Gestión para Barbería.pdf` es el PRD aprobado. Sus requisitos funcionales y no funcionales están transcritos y trazados en [`specs.md`](specs.md).
- [`implementation.md`](implementation.md) describe decisiones técnicas y el plan de cambio. [`diagram.puml`](diagram.puml) es el diagrama de clases editable y la fuente de verdad; `diagram.png` es la exportación anterior y debe regenerarse con PlantUML cuando se disponga del renderer.
- En caso de discrepancia entre el PRD y este archivo, prevalece el PRD. Este archivo gobierna las convenciones del código, no redefine requisitos de negocio.

## Proyecto y nombres reales

- Java SE 17, Maven, Swing, SQLite JDBC; paquete base `com.monkey`.
- Capas existentes: `com.monkey.ui`, `com.monkey.logic`, `com.monkey.data_persistance` y `com.monkey.model`.
- Respeta los nombres actuales: `cita`, `servicios`, `venta`, `conexion_DB`, `GeneradorCSV` y `Repositorio*`. No renombres paquetes o clases para que coincidan con diagramas antiguos.
- Punto de entrada: `src/main/java/com/monkey/App.java`.
- Pruebas: `src/test/java/com/monkey/`, organizadas por paquete/capa.

## Arquitectura

1. Flujo permitido: `ui -> logic -> data_persistance`; `logic` y persistencia pueden usar `model`. `App` ensambla dependencias. `ui` no importa persistencia ni JDBC.
2. Inyección manual por constructor. No crear clases de conveniencia, fábricas, controladores ni paquetes sin necesidad documentada. `RepositorioPin` es la única ampliación autorizada por el requisito PRD de credenciales; persiste solo el hash local y no usa SQLite.
3. Mantén capas sencillas, código imperativo legible, layouts Swing simples y mensajes de error en español. No uses hilos para expiración de sesión.
4. Dinero: solo `BigDecimal`, escala de dos decimales y `RoundingMode.HALF_UP`. Fechas: API `java.time` (salvo conversión local de `JSpinner`).
5. Las tablas Swing son de solo lectura y las filas se asocian a listas de modelos, sin reconstruir objetos desde texto.
6. Consultas JDBC parametrizadas. CSV en UTF-8 con BOM, escape RFC 4180 y neutralización de celdas que comiencen con `=`, `+`, `-` o `@`.
7. Mantén íntegros los registros históricos de venta y la atomicidad del cobro.

## Credencial administrativa

- El administrador configura una clave alfanumérica de 6 a 12 caracteres; el cambio exige la clave anterior. La primera ejecución conserva la credencial inicial definida por el PRD para compatibilidad y debe permitir cambiarla desde la pestaña Catálogo.
- La contraseña/PIN no se guarda en la base de datos ni en texto plano. Usa hash PBKDF2 con sal aleatoria, compara en tiempo constante y persiste el formato versionado en `MonkeyElegantSystem/pin.conf` mediante `RepositorioPin`.
- Si `pin.conf` contiene el formato heredado en texto plano, migra a hash al iniciar. No sobrescribas un hash desconocido o corrupto con el valor por defecto; falla de forma explícita y segura.
- La sesión caduca tras 10 minutos de inactividad. El diálogo de cambio siempre solicita la credencial anterior, aunque la sesión esté activa.

## Regla de pruebas

- Antes de implementar comportamiento, agrega o actualiza pruebas identificables en `src/test/java` para la unidad y el flujo integrado afectado. No alteres pruebas para ocultar fallos.
- Ejecuta todas las pruebas tras el cambio. Para UI, contempla `GraphicsEnvironment.isHeadless()` sin omitir las pruebas de lógica/persistencia.
- No afirmes pruebas aprobadas si no se ejecutaron. No edites artefactos compilados a mano.

## Alcance de archivos

- Se permite modificar código existente cuando sea necesario para satisfacer el PRD, con cambios acotados y pruebas previas. Conserva constructores y APIs existentes si es posible; si se amplían, mantén sobrecargas compatibles.
- Antes de tocar archivos de SQLite o ventas, revisa cómo protegen transacciones e historial. El cambio de credenciales no necesita tabla nueva ni cambio al esquema ER.
