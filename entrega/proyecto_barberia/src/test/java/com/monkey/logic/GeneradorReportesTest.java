package com.monkey.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.monkey.data_persistance.GeneradorCSV;
import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.model.cita;
import com.monkey.model.servicios;
import com.monkey.model.venta;

/**
 * Capa 2 - Pruebas del generador de reportes.
 * Verifica los dos reportes CSV independientes (RF 18) y el formato de salida
 * con marca BOM y escape de celdas (RNF 13).
 */
public class GeneradorReportesTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;
    private RepositorioVentas repoVentas;
    private GeneradorReportes generadorReportes;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
        repoVentas = new RepositorioVentas(db, repoCitas);
        generadorReportes = new GeneradorReportes(repoVentas, new GeneradorCSV());

        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    private void registrarVenta(String cliente, LocalDateTime fechaHora, String total, String efectivo) {
        List<servicios> serviciosCita = new ArrayList<>(repoServicios.listarActivos());
        cita nueva = new cita(cliente, fechaHora.toLocalDate(), fechaHora.toLocalTime(), serviciosCita);
        repoCitas.insertar(nueva);

        BigDecimal monto = new BigDecimal(total);
        BigDecimal recibido = new BigDecimal(efectivo);
        StringBuilder snapshot = new StringBuilder();
        for (servicios servicio : serviciosCita) {
            if (snapshot.length() > 0) {
                snapshot.append("; ");
            }
            snapshot.append(servicio.getNombre().trim())
                    .append(" ($").append(servicio.getCostoUnitario().toPlainString()).append(")");
        }
        venta ventaPrueba = new venta(0, nueva, snapshot.toString(), monto, recibido,
                recibido.subtract(monto).setScale(2), fechaHora);
        repoVentas.registrarVentaTransaccional(ventaPrueba);
    }

    private String leerArchivo(File archivo) throws IOException {
        byte[] contenido = Files.readAllBytes(archivo.toPath());
        assertEquals("El archivo debe iniciar con la marca BOM.", (byte) 0xEF, contenido[0]);
        return new String(contenido, StandardCharsets.UTF_8);
    }

    @Test
    public void exportarDetalladoGeneraElEncabezadoYLosTickets() throws IOException {
        LocalDateTime fechaHora = LocalDateTime.now().minusHours(2);
        registrarVenta("Ana Torres", fechaHora, "50.00", "100.00");

        File destino = new File(carpeta.getRoot(), "detallado.csv");
        generadorReportes.exportarDetallado(destino);

        String contenido = leerArchivo(destino);
        assertTrue(contenido.contains("ID Venta,Fecha y Hora,Cliente,Servicios Cobrados,Total,Efectivo,Cambio"));
        assertTrue(contenido.contains("Ana Torres"));
        assertTrue(contenido.contains("Corte ($50.00)"));
        assertTrue(contenido.contains("50.00"));
        assertTrue(contenido.contains("100.00"));
    }

    @Test
    public void exportarDetalladoRespetaElRangoDeFechas() throws IOException {
        registrarVenta("Ana Torres", LocalDateTime.now().minusDays(40), "50.00", "100.00");
        registrarVenta("Luis Diaz", LocalDateTime.now().minusDays(1), "50.00", "100.00");

        File destino = new File(carpeta.getRoot(), "rango.csv");
        generadorReportes.exportarDetallado(destino, LocalDate.now().minusDays(7), LocalDate.now());

        String contenido = leerArchivo(destino);
        assertTrue(contenido.contains("Luis Diaz"));
        assertTrue("El reporte debe respetar el rango de fechas.", !contenido.contains("Ana Torres"));
    }

    @Test
    public void exportarAcumuladoAgrupaPorDia() throws IOException {
        LocalDateTime hoy = LocalDateTime.now().minusHours(3);
        registrarVenta("Ana Torres", hoy, "50.00", "100.00");
        registrarVenta("Luis Diaz", hoy, "30.00", "30.00");

        File destino = new File(carpeta.getRoot(), "acumulado.csv");
        generadorReportes.exportarAcumuladoMensual(destino, YearMonth.from(hoy));

        String contenido = leerArchivo(destino);
        assertTrue(contenido.contains("Periodo (Año-Mes),Fecha,Cantidad de Ventas,Total Diario"));
        assertTrue(contenido.contains(YearMonth.from(hoy).toString()));
        assertTrue(contenido.contains("2"));
        assertTrue(contenido.contains("80.00"));
    }

    @Test
    public void exportarAcumuladoRespetaElPeriodoSeleccionado() throws IOException {
        registrarVenta("Ana Torres", LocalDateTime.now().minusHours(2), "50.00", "100.00");

        File destino = new File(carpeta.getRoot(), "otro_mes.csv");
        try {
            generadorReportes.exportarAcumuladoMensual(destino, YearMonth.now().minusMonths(2));
            fail("No hay ventas en el periodo indicado.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No hay ventas"));
        }
    }

    @Test
    public void unReporteSinDatosNoGeneraArchivo() {
        File destino = new File(carpeta.getRoot(), "vacio.csv");
        try {
            generadorReportes.exportarDetallado(destino);
            fail("No se debe generar un reporte sin datos.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No hay ventas"));
        }
        assertTrue(!destino.exists());
    }

    @Test
    public void losPeriodosDisponiblesEstanOrdenadosDelMasRecienteAlMasAntiguo() {
        registrarVenta("Ana Torres", LocalDateTime.now().minusHours(2), "50.00", "100.00");
        registrarVenta("Luis Diaz", LocalDateTime.now().minusDays(70), "50.00", "100.00");

        List<YearMonth> periodos = generadorReportes.obtenerPeriodosDisponibles();
        assertEquals(2, periodos.size());
        assertTrue(periodos.get(0).isAfter(periodos.get(1)));
    }

    @Test
    public void elSnapshotConFormulaDeExcelQuedaNeutralizado() throws IOException {
        List<servicios> catalogo = repoServicios.listarTodos();
        servicios servicioRenombrado = catalogo.get(0);
        servicioRenombrado.setNombre("=SUM(A1)");
        repoServicios.actualizar(servicioRenombrado);

        registrarVenta("Ana Torres", LocalDateTime.now().minusHours(1), "50.00", "100.00");

        File destino = new File(carpeta.getRoot(), "inyeccion.csv");
        generadorReportes.exportarDetallado(destino);

        String contenido = leerArchivo(destino);
        assertTrue("La celda con formula debe quedar neutralizada con apostrofo.",
                contenido.contains("'=SUM(A1)"));
    }

    @Test
    public void elGeneradorExigeRepositorioYEscritor() {
        try {
            new GeneradorReportes(null, new GeneradorCSV());
            fail("El repositorio de ventas es obligatorio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatorio"));
        }
        try {
            new GeneradorReportes(repoVentas, null);
            fail("El escritor CSV es obligatorio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatorio"));
        }
    }
}
