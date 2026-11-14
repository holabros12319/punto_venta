# REGLAS ESTRICTAS DE ARQUITECTURA Y GENERACIÓN DE CÓDIGO
**Proyecto:** MONKEY ELEGANT SYSTEM  
**Stack:** Java SE 17 · Maven · Java Swing · SQLite Embebido (JDBC)  
**Paquete Base:** `com.monkey`  

> **INSTRUCCIÓN OBLIGATORIA:**  
> Este documento es de cumplimiento obligatorio. Antes de generar o modificar cualquier archivo `.java`, lee y cumple todas las reglas de este documento. Si una solicitud del usuario contradice estas reglas, rechaza la acción e indica qué regla se está violando.

---

## CONDICION QUE JAMAS DEBE DE VIOLARSE: INMUTABILIDAD DE CÓDIGO YA TESTEADO

**LAS CAPAS 3 Y 4 ESTÁN 100 % COMPLETADAS Y TESTEADAS. ESTÁ TERMINANTEMENTE PROHIBIDO MODIFICAR, REFACTORIZAR, RENOMBRAR O ALTERAR CUALQUIERA DE SUS ARCHIVOS.**

1. **Acceso de Solo Lectura:** El agente **debe leer** los archivos de `com.monkey.model` y `com.monkey.persistence` antes de escribir código para verificar las firmas exactas de sus constructores y métodos públicos, pero **jamás** puede editarlos.
2. **Archivos Bloqueados (No Tocar Bajo Ninguna Circunstancia):**
   * `src/main/java/com/monkey/model/Servicio.java`
   * `src/main/java/com/monkey/model/Cita.java`
   * `src/main/java/com/monkey/model/Venta.java`
   * `src/main/java/com/monkey/persistence/ConexionSQL.java`
   * `src/main/java/com/monkey/persistence/RepositorioServicios.java`
   * `src/main/java/com/monkey/persistence/RepositorioCitas.java`
   * `src/main/java/com/monkey/persistence/RepositorioVentas.java`
   * `src/main/java/com/monkey/persistence/EscritorCSV.java`
3. **Resolución de Errores de Compilación:** Si al programar `logic` (Capa 2) o `ui` (Capa 1) ocurre un error de tipos, sobrecargas o parámetros, **la adaptación debe hacerse exclusivamente dentro de `logic` o `ui`**, jamás alterando las clases bloqueadas.

---

## CONTRATO EXACTO DEL DIAGRAMA DE CLASES

Respeta exactamente los nombres de clases, atributos y métodos definidos en el diagrama de arquitectura:

### Capa 2: Lógica de Negocio (`com.monkey.logic`)
1. **`SesionAdmin`**
   * Atributos: `-autenticado : boolean`, `-pinConfigurado : String`, `-ultimoAcceso : LocalDateTime`
   * Constructor: `public SesionAdmin(String pinConfigurado)`
   * Métodos:
     * `public boolean validarPin(String pinIngresado)`
     * `public boolean estaAutenticado()` *(expira pasivamente si pasaron 10 minutos desde `ultimoAcceso`; si sigue activo, renueva `ultimoAcceso = LocalDateTime.now()`)*
     * `public void cerrarSesion()`
2. **`GestorCatalogo`**
   * Atributos: `-repoServicios : RepositorioServicios`, `-categoriasFijas : List<String>` *(inicializada con `"Cortes de pelo"` y `"Otros servicios"`)*
   * Constructor: `public GestorCatalogo(RepositorioServicios repoServicios)`
   * Métodos:
     * `public void guardarServicio(Servicio servicio)`
     * `public void editarServicio(Servicio servicio)`
     * `public List<Servicio> filtrarServicios(String texto, boolean soloActivos)`
     * `private void validarServicio(Servicio servicio)` *(exige nombre no vacío tras `trim()`, `costoUnitario > 0` y pertenencia a `categoriasFijas`)*
3. **`GestorAgenda`**
   * Atributos: `-repoCitas : RepositorioCitas`, `-horariosBase : List<LocalTime>` *(bloques operativos fijos de `09:00` a `19:00` cada hora)*
   * Constructor: `public GestorAgenda(RepositorioCitas repoCitas)`
   * Métodos:
     * `public void registrarCita(Cita cita)`
     * `public List<LocalTime> obtenerHorariosDisponibles(LocalDate fecha)` *(resta `repoCitas.obtenerHorariosOcupados(fecha)` y, si `fecha` es hoy, descarta horas anteriores a `LocalTime.now()`)*
     * `public List<Cita> buscarCitas(String cliente, LocalDate fecha)`
     * `public void cambiarEstadoCita(int idCita, String nuevoEstado)` *(consulta cita actual; si es `"Completada"` o `"Cancelada"`, lanza `IllegalStateException`)*
     * `private void validarDatosCita(Cita cita)` *(valida cliente no vacío, lista de servicios no vacía y `LocalDateTime.of(fecha, horario)` no anterior a `LocalDateTime.now()`)*
4. **`GestorPOS`**
   * Atributos: `-repoCitas : RepositorioCitas`, `-repoVentas : RepositorioVentas`
   * Constructor: `public GestorPOS(RepositorioCitas repoCitas, RepositorioVentas repoVentas)`
   * Métodos:
     * `public List<Cita> buscarCitasPorCobrar(String cliente)`
     * `public BigDecimal calcularCambio(BigDecimal total, BigDecimal efectivo)` *(exige `total > 0` y `efectivo >= total`)*
     * `public Venta procesarCobro(int idCita, BigDecimal efectivo)` *(reconsulta `repoCitas.obtenerPorId(idCita)` desde SQL, exige estado `"Pendiente"` o `"Confirmada"`, arma `snapshotServicios`, calcula cambio y llama a `repoVentas.registrarVentaTransaccional`)*
     * `private String construirSnapshotServicios(List<Servicio> servicios)`
5. **`GeneradorReportes`**
   * Atributos: `-repoVentas : RepositorioVentas`, `-escritorCSV : EscritorCSV`
   * Constructor: `public GeneradorReportes(RepositorioVentas repoVentas, EscritorCSV escritorCSV)`
   * Métodos:
     * `public void exportarDetallado(File destino)` *(encabezado: `ID Venta,Fecha y Hora,Cliente,Servicios Cobrados,Total,Efectivo,Cambio`; llama a `escritorCSV.escribirLineas(destino, lineas)`)*
     * `public void exportarAcumuladoMensual(File destino)` *(agrupa por `YearMonth` y `LocalDate`; encabezado: `Periodo (Año-Mes),Fecha,Cantidad de Ventas,Total Diario`; llama a `escritorCSV.escribirLineas(destino, lineas)`)*
     * `private void validarDatosNoVacios(List<Venta> ventas)` *(lanza `IllegalStateException` si la lista está vacía)*

### Capa 1: Interfaz de Usuario (`com.monkey.ui`)
1. **`VentanaPrincipal`** *(extiende `JFrame`)*
   * Atributos: `-panelPestanas : JTabbedPane`, `-vistaAgenda : VistaAgenda`, `-vistaCatalogo : VistaCatalogo`, `-vistaPOS : VistaPOS`, `-sesionAdmin : SesionAdmin`
   * Constructor: `public VentanaPrincipal(GestorAgenda, GestorCatalogo, GestorPOS, GeneradorReportes, SesionAdmin)`
   * Métodos: `public void iniciar()`, `public boolean verificarAccesoAdmin()`, `private void configurarCambioPestanas()`
2. **`VistaAgenda`** *(extiende `JPanel`)*
   * Atributos: `-gestorAgenda : GestorAgenda`, `-gestorCatalogo : GestorCatalogo`, `-txtCliente : JTextField`, `-selectorFecha : JSpinner`, `-comboHorarios : JComboBox<LocalTime>`, `-listaServicios : JList<Servicio>`, `-txtBuscar : JTextField`, `-tablaCitas : JTable` *(más `List<Cita> citasActuales` para selección segura)*
   * Constructor: `public VistaAgenda(GestorAgenda gestorAgenda, GestorCatalogo gestorCatalogo)`
   * Métodos: `public void refrescarHorarios()`, `public void refrescarServicios()`, `public void cargarTablaCitas()`, `private void onGuardarCita()`, `private void onCambiarEstado(String estado)`, `private void limpiarFormulario()`
3. **`VistaCatalogo`** *(extiende `JPanel`)*
   * Atributos: `-gestorCatalogo : GestorCatalogo`, `-ventanaPadre : VentanaPrincipal`, `-txtNombre : JTextField`, `-txtPrecio : JTextField`, `-comboCategoria : JComboBox<String>`, `-chkActivo : JCheckBox`, `-txtFiltro : JTextField`, `-tablaServicios : JTable` *(más `List<Servicio> serviciosActuales` e `int idServicioSeleccionado`)*
   * Constructor: `public VistaCatalogo(GestorCatalogo gestorCatalogo, VentanaPrincipal ventanaPadre)`
   * Métodos: `public void cargarTablaServicios()`, `private void onGuardarOActualizar()`, `private void limpiarFormulario()`
4. **`VistaPOS`** *(extiende `JPanel`)*
   * Atributos: `-gestorPOS : GestorPOS`, `-generadorReportes : GeneradorReportes`, `-ventanaPadre : VentanaPrincipal`, `-citaActiva : Cita`, `-txtBuscar : JTextField`, `-tablaPendientes : JTable`, `-lblTotal : JLabel`, `-txtEfectivo : JTextField`, `-lblCambio : JLabel`, `-btnCobrar : JButton` *(más `List<Cita> citasPendientesActuales`)*
   * Constructor: `public VistaPOS(GestorPOS gestorPOS, GeneradorReportes generadorReportes, VentanaPrincipal ventanaPadre)`
   * Métodos: `public void buscarCitasActivas()`, `private void onSeleccionarCita()`, `private void onActualizarCambioEnVivo()`, `private void onProcesarCobro()`, `private void onExportarCSV(boolean detallado)`
5. **`DialogoPinAdmin`** *(extiende `JDialog`)*
   * Atributos: `-campoPin : JPasswordField`, `-confirmado : boolean`
   * Método: `public static boolean solicitarYValidar(Component padre, SesionAdmin sesion)`

---

## REGLAS DE DESARROLLO

### Bloque A: Arquitectura y Fronteras de Capa

1. **Flujo Unidireccional Estricto (`ui` $\rightarrow$ `logic` $\rightarrow$ `persistence`):**
   * **`com.monkey.ui`** solo puede importar: `com.monkey.logic.*`, `com.monkey.model.*`, `javax.swing.*`, `javax.swing.table.*`, `javax.swing.event.*`, `javax.swing.border.*`, `javax.swing.filechooser.FileNameExtensionFilter`, `java.awt.*`, `java.awt.event.*`, `java.math.BigDecimal`, `java.math.RoundingMode`, `java.time.*`, `java.time.format.DateTimeFormatter`, `java.io.File`, `java.util.List` y `java.util.ArrayList`. *(Prohibido importar `com.monkey.persistence.*` o `java.sql.*`)*.
   * **`com.monkey.logic`** solo puede importar: `com.monkey.persistence.*`, `com.monkey.model.*`, `java.math.BigDecimal`, `java.math.RoundingMode`, `java.time.*`, `java.time.format.DateTimeFormatter`, `java.time.temporal.ChronoUnit`, `java.io.File`, `java.util.List`, `java.util.ArrayList`, `java.util.Map`, `java.util.TreeMap` y `java.util.LinkedHashMap`. *(Prohibido importar `com.monkey.ui.*`, `javax.swing.*`, `java.awt.*` o `java.sql.*`)*.
2. **Prohibido Inventar Clases, Interfaces o Paquetes Nuevos:**
   * Cíñete exclusivamente a las 18 clases del diagrama aprobado. No crees interfaces DAO, clases `Helper`, `Util`, `Controller`, `DTO` ni subpaquetes adicionales.
3. **Inyección Manual por Constructor:**
   * Ninguna clase debe instanciar servicios o repositorios con `new` internamente; todo se recibe por constructor en atributos `private final`. *(Únicas excepciones: `VentanaPrincipal` crea sus 3 vistas hijas en su constructor, y `DialogoPinAdmin` se instancia dentro de `solicitarYValidar`).*

### Bloque B: Prohibición de Sobreingeniería y Código Críptico

4. **Cero Patrones de Diseño Complejos:**
   * Prohibido usar *Builders*, *Factories*, *Observers* personalizados, *Command*, *Reflections*, *Generics* complejos o inyección automática.
5. **Java Imperativo y Legible:**
   * Prohibido usar *Streams* encadenados (`.stream().filter().map()...`), lambdas multilínea o ternarios anidados. Usa bucles `for-each` tradicionales (`for (Cita c : lista)`) y estructuras `if / else` claras. *(Se permiten lambdas de una línea únicamente en `addActionListener(e -> ...)` y `ChangeListener`).*
6. **Cero Dependencias Externas Adicionales en `pom.xml`:**
   * No agregues librerías externas de UI, fechas o utilidades. Todo funciona con Java SE 17 estándar y el motor embebido `sqlite-jdbc` ya instalado.
7. **Layouts Simples en Swing (`ui`):**
   * Usa únicamente `BorderLayout`, `GridLayout`, `FlowLayout` o `BoxLayout`. Prohibido usar `GridBagLayout` o `setLayout(null)` con `setBounds(...)`.

### Bloque C: Integridad Financiera, Temporal y de Datos

8. **Uso Exclusivo de `BigDecimal` para Dinero:**
   * Prohibido usar `double` o `float` para precios, totales, efectivo o cambio. Usa `BigDecimal` con escala 2 y `RoundingMode.HALF_UP`.
9. **Uso Exclusivo de `java.time` para Fechas y Horas:**
   * Usa `LocalDate`, `LocalTime`, `LocalDateTime` y `YearMonth`. *(Única excepción: `java.util.Date` puede usarse localmente en una línea dentro de `VistaAgenda` para convertir el valor de `JSpinner` con `SpinnerDateModel` hacia `LocalDate`).*
10. **Cero Lógica de Negocio en `ui`:**
    * Los métodos de `ui` solo leen controles, validan formato numérico básico, llaman a `logic` y refrescan vistas. Prohibido calcular totales, cambios, horarios disponibles o líneas CSV dentro de `com.monkey.ui`.
    * En `VistaPOS.onActualizarCambioEnVivo()`, si `txtEfectivo` está vacío o incompleto mientras se teclea, muestra `"$0.00"` en `lblCambio` sin lanzar `JOptionPane`.
    * Al confirmar un cobro en `VistaPOS`, muestra en el mensaje de éxito el nombre del cliente, el total cobrado y el cambio devuelto (no imprimas `venta.getIdVenta()` porque el objeto devuelto en memoria tiene `idVenta = 0`).

### Bloque D: Prevención de Fallos Catastróficos y Empaquetado Local

11. **Revalidación Fresca en `GestorPOS`:**
    * `GestorPOS.procesarCobro(int idCita, BigDecimal efectivo)` debe consultar la cita fresca en SQL con `repoCitas.obtenerPorId(idCita)` y verificar que siga `"Pendiente"` o `"Confirmada"` antes de cobrar. Nunca confíes en el estado de un objeto en memoria.
12. **Bloqueo Preventivo de Botones (`ui`):**
    * Todo botón de acción crítica (`Guardar`, `Confirmar`, `Cancelar`, `Cobrar`, `Exportar CSV`) debe ejecutar `boton.setEnabled(false)` al inicio del clic y `boton.setEnabled(true)` dentro de un bloque `finally`.
13. **Sincronización Automática entre Pestañas y Carga Inicial (`VentanaPrincipal`):**
    * Registra un `ChangeListener` en el `JTabbedPane` para ejecutar los métodos de refresco de la pestaña activa cada vez que el usuario cambie de vista.
    * En `VentanaPrincipal.iniciar()`, ejecuta explícitamente la carga inicial de la primera pestaña (`VistaAgenda`) antes de llamar a `setVisible(true)`.
14. **Control de Acceso Pasivo por PIN (Sin Hilos):**
    * Las acciones de edición/alta en `VistaCatalogo` y exportación CSV en `VistaPOS` deben validar `ventanaPadre.verificarAccesoAdmin()`.
    * La expiración de 10 minutos en `SesionAdmin` se evalúa comparando `LocalDateTime.now()` contra `ultimoAcceso`. Prohibido crear hilos (`Thread`, `Timer`, `SwingWorker`, `ExecutorService`).
15. **Tablas `JTable` de Solo Lectura y Listas Paralelas (`ui`):**
    * Todo `DefaultTableModel` debe sobrescribir `isCellEditable` retornando `false`.
    * Cada vista con `JTable` debe mantener una `List` privada sincronizada con las filas mostradas para obtener el objeto seleccionado mediante `lista.get(tabla.getSelectedRow())` sin parsear texto desde las celdas.
    * Los métodos `limpiarFormulario()` deben resetear explícitamente cualquier ID o entidad seleccionada (`idCitaSeleccionada = 0`, `idServicioSeleccionado = 0`, `citaActiva = null`) y llamar a `tabla.clearSelection()`.
16. **Manejo Explícito y Amigable de Errores:**
    * Prohibido dejar bloques `catch` vacíos o usar solo `e.printStackTrace()`. `logic` lanza `IllegalArgumentException` o `IllegalStateException` en español; `ui` las captura y muestra mediante `JOptionPane`.
17. **Sanitización de Entradas de Usuario y Seguridad CSV:**
    * Todo texto de `JTextField` debe limpiarse con `.trim()` antes de procesarse. Captura `NumberFormatException` en `ui` al parsear precios o efectivo.
    * Al armar líneas CSV en `GeneradorReportes`, asegura que cualquier celda que inicie con `=`, `+`, `-` o `@` sea neutralizada (prefijo `'`) y que comas/comillas sean escapadas si `EscritorCSV.escribirLineas` no lo hace internamente.
18. **Base de Datos Autónoma Local y Rol Exclusivo de `App.java`:**
    * El sistema debe operar 100 % local mediante SQLite embebido (`sqlite-jdbc`), sin requerir servidores externos (PostgreSQL/MySQL) ni herramientas externas (pgAdmin).
    * `src/main/java/com/monkey/App.java` crea la carpeta local del usuario (`System.getProperty("user.home") + File.separator + "MonkeyElegantSystem"`), conecta `ConexionSQL` a `"jdbc:sqlite:" + rutaDb`, ejecuta `conexion.inicializarTablas()`, ensambla las dependencias (`persistence` $\rightarrow$ `logic` $\rightarrow$ `ui`) e inicia `VentanaPrincipal` dentro de `SwingUtilities.invokeLater`.
19. **Testing:**
* Por ningun motivo se debe de implementar un bloque de codigo o funcion sin antes realizar pruebas unitarias y  en conjunto por capa. Estas pruebas deben ser almacenadas con un nombre que facilite su identificacion y localización en la carpeta llamata "test"