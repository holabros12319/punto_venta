# Especificación de requisitos — MONKEY BARBER SYSTEM

**Fuente:** PRD `MONKEY BARBER SYSTEM — Sistema de Gestión para Barbería.pdf` (versión v4.0, 13/11/2026). Esta especificación organiza los requisitos del PRD para su trazabilidad con el código. `AGENTS.md` fija convenciones técnicas y nombres reales del repositorio; no sustituye al PRD.

## 1. Propósito, usuarios y alcance

El sistema de escritorio de MONKEY ELEGANT gestiona agenda y citas, catálogo de servicios, cobros en efectivo, historial y exportación de ingresos. Sustituye registros manuales, evita empalmes y reduce errores de captura/cálculo.

**Perfiles:** Administrador (gestiona catálogo y exporta reportes); Barbero/Recepcionista (agenda, consulta catálogo, administra estados y cobra). Ambos pueden consultar horarios y cobrar. Las operaciones restringidas requieren sesión administrativa vigente.

**Dentro del alcance:** agenda local; catálogo CRUD con dos categorías; POS en efectivo; SQLite local; CSV de detalle y acumulado mensual; UI Swing. **Fuera del alcance:** sincronización externa de calendarios, inventario físico, tarjeta/pasarelas/hardware de cobro, mensajería o notificaciones externas, facturación fiscal, PDF/email y conectividad Wi-Fi.

## 2. Requisitos funcionales

| ID PRD | Requisito verificable | Criterio de aceptación resumido |
|---|---|---|
| RF1 | Configurar e inicializar credencial administrativa | Hay credencial inicial; el administrador puede definir/cambiar una clave alfanumérica de 6–12 caracteres. Para cambiarla se verifica la anterior. Se almacena como hash, nunca en la base de datos ni en texto plano. La sesión expira después de 10 minutos sin actividad y se renueva con actividad. |
| RF2 | Autorizar operaciones restringidas | Antes de alta/edición/desactivación del catálogo o exportar CSV se exige PIN correcto y sesión vigente; PIN incorrecto deniega la acción. |
| RF3 | Categorías fijas | Cada servicio pertenece exactamente a `Cortes de pelo` o `Otros servicios`. |
| RF4 | Alta de servicio | Administrador registra nombre, precio unitario positivo y categoría. No se admite duplicado por nombre ignorando mayúsculas/minúsculas; un servicio inactivo existente se reactiva, no se duplica. |
| RF5 | Edición de servicio | Administrador puede cambiar nombre, precio y categoría. Los cambios afectan citas/cobros futuros; no reescriben ventas históricas. |
| RF6 | Baja lógica | Administrador desactiva un servicio; desaparece del selector para citas nuevas, pero no se borran citas/ventas históricas relacionadas. |
| RF7 | Consulta aislada del catálogo | Administrador y recepción pueden listar activos/todos y filtrar por coincidencia parcial de nombre/estado; la consulta no altera la vista de agenda. |
| RF8 | Consultar horarios | Para una fecha desde hoy se muestran bloques de operación libres; en el día actual se descartan horas anteriores. La cancelación debe devolver el bloque a disponibilidad. |
| RF9 | Registrar cita | Captura cliente, fecha, hora de inicio y uno o más servicios activos; el sistema guarda y muestra también la hora de término. Como la agenda opera en bloques fijos de una hora, el término se calcula como inicio + 1 hora. La cita se crea `Pendiente`. No se admiten cliente vacío ni cita pasada. |
| RF10 | Evitar empalmes | El sistema y SQLite impiden dos citas activas simultáneas en el mismo horario. |
| RF11 | Estados previos al cobro | Una cita no cobrada puede estar `Pendiente`, `Confirmada` o `Cancelada`. `Completada` no se asigna manualmente; solo al registrar el cobro. |
| RF12 | Liberar horario al cancelar | Al cancelar una cita se libera inmediatamente su bloque e impide operar nuevamente sobre esa cita cancelada. |
| RF13 | Buscar citas por cliente | Agenda y POS localizan citas con coincidencia parcial del nombre. |
| RF14 | Importar cita cobrable al POS | POS lista/busca citas `Pendiente` o `Confirmada` y muestra cliente, servicios y total acumulado. |
| RF15 | Calcular total y cambio | Muestra el total de los servicios, exige efectivo suficiente y calcula cambio exacto no negativo. |
| RF16 | Registrar venta transaccional | Al cobrar se consulta el estado vigente en SQLite, se registra venta con snapshot de nombres/precios y se marca la cita `Completada` en la misma transacción. Un error revierte ambos cambios. Una cita genera como máximo una venta. |
| RF17 | Comprobante visual | Después del cobro se muestra MONKEY ELEGANT, ID de venta, fecha/hora, cliente, servicios, total, efectivo y cambio. |
| RF18 | Exportar reportes CSV | Con autorización se exportan: (a) detalle por rango de fechas con ID, fecha/hora, cliente, servicios y total; (b) ingresos diarios agrupados en el año/mes seleccionado. |

### Matriz de acceso del PRD

| Operación | Administrador | Barbero/Recepcionista |
|---|---|---|
| Consultar horarios y citas | Permitido | Permitido |
| Cambiar estado previo al cobro | Permitido | Permitido |
| Consultar catálogo | Permitido | Permitido |
| Alta/edición/desactivación catálogo | Permitido con credencial | Solo lectura |
| Cobrar en POS | Permitido | Permitido |
| Exportar CSV | Permitido con credencial | Denegado |

## 3. Requisitos no funcionales

| ID de referencia | Requisito verificable |
|---|---|
| RNF seguridad/PRD 9.1 | Captura de credencial en `JPasswordField`; validación por `SesionAdmin`; hash con sal; no persistir credencial en SQLite ni en claro. |
| RNF SQL/PRD 9.1 | JDBC con `PreparedStatement`; búsquedas `LIKE` escapan `%`, `_` y el carácter de escape; no concatenar entradas del usuario en SQL. |
| RNF catálogo/PRD 9.2 | Consultar catálogo desde administración no altera el selector de servicios ni la agenda. |
| RNF historial/PRD 9.2 | Ediciones y bajas lógicas no alteran ventas pasadas. |
| RNF horarios/PRD 9.3 | Índice único parcial de SQLite para bloque activo `(fecha, horario)` de citas `Pendiente`/`Confirmada`. |
| RNF temporal/PRD 9.3 | Persistir fecha/hora con formato ISO-8601 y usar `java.time`. |
| RNF financiero/PRD 9.4 | `BigDecimal` para dinero, escala 2, sin `double`/`float`; persistencia numérica que no introduzca redondeo. |
| RNF atomicidad/PRD 9.4 | Venta y cambio de estado se ejecutan en una transacción con rollback ante error. |
| RNF inmutabilidad/PRD 9.4 | Triggers SQLite impiden actualizar/borrar ventas y modificar citas completadas. |
| RNF CSV/PRD 9.4 | RFC 4180, UTF-8 con BOM, comillas/comas escapadas y neutralización de fórmulas iniciadas por `=`, `+`, `-`, `@`. |
| RNF arquitectura/PRD 9.5 | Capas desacopladas `com.monkey.ui`, `com.monkey.logic`, `com.monkey.data_persistance`, `com.monkey.model`; sin dependencias circulares; inyección explícita. |
| RNF UI/PRD 9.5 | Cambio de pestaña refresca vista activa. Botones de operación crítica se desactivan durante su acción para evitar doble envío. Sin hilos secundarios para sincronización/expiración. |
| RNF local/PRD 9.5 | Aplicación ejecutable local con SQLite embebido y archivos en el directorio del usuario; sin servidor ni red. |

**Nota de trazabilidad:** el PRD reutiliza los números RNF 11 y 12 para dos apartados distintos (atomicidad/arquitectura y triggers/sincronización). En esta especificación se conserva la referencia de sección para desambiguarlos.

## 4. Invariantes de datos

1. No existen dos citas activas para la misma fecha/bloque.
2. No se registra una cita anterior a la fecha/hora actual.
3. Toda cita contiene al menos un servicio y todo cliente/servicio tiene nombre no vacío.
4. Una cita `Cancelada` o `Completada` no vuelve a estado activo ni se modifica.
5. Solo `Pendiente` y `Confirmada` se cobran; una cita se liquida como máximo una vez.
6. Total = suma exacta de servicios; precio/total > 0; efectivo >= total; cambio = efectivo − total y no es negativo.
7. Nombre del servicio es único sin distinguir mayúsculas/minúsculas; categorías cerradas.
8. No se elimina un servicio o cita que participa en registros vinculados; las ventas son inmutables.
9. Alta/edición/baja de catálogo y exportación requieren sesión de administrador vigente.

## 5. Brechas conocidas frente al código revisado

- RF1: falta UI de definición/cambio; PIN predeterminado `monkey2026` queda en claro en `pin.conf`; falta pedir PIN anterior en un flujo de cambio.
- RF9: el término se guarda en `citas.hora_fin` y se deriva del bloque fijo de 60 minutos; los registros previos se migran calculando `horario + 1 hora`.
- El diagrama PNG y el `AGENTS.md` anterior usaban nombres distintos del código. La fuente editable corregida es `diagram.puml`.

El plan de corrección y el mapa archivo/requisito están en [`implementation.md`](implementation.md).
