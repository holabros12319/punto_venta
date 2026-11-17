package com.monkey.data_persistance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.monkey.model.cita;
import com.monkey.model.servicios;
import com.monkey.model.venta;

/**
 * Capa 3 - Pruebas del repositorio de ventas.
 * Verifica la atomicidad del cobro, la unicidad de liquidacion por cita y la
 * inmutabilidad de los registros financieros.
 */
public class RepositorioVentasTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;
    private RepositorioVentas repoVentas;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
        repoVentas = new RepositorioVentas(db, repoCitas);
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    private servicios registrarServicio(String nombre, String precio) {
        servicios nuevo = new servicios(nombre, new BigDecimal(precio), "Cortes de pelo");
        repoServicios.insertar(nuevo);
        List<servicios> guardados = repoServicios.listarTodos();
        return guardados.get(guardados.size() - 1);
    }

    private cita registrarCita(String cliente, LocalDate fecha, LocalTime hora, String nombreServicio) {
        servicios servicio = registrarServicio(nombreServicio, "50.00");
        List<servicios> serviciosCita = new ArrayList<>();
        serviciosCita.add(servicio);
        cita nueva = new cita(cliente, fecha, hora, serviciosCita);
        repoCitas.insertar(nueva);
        return nueva;
    }

    @Test
    public void registrarVentaTransaccionalMarcaLaCitaComoCompletada() {
        cita citaPrueba = registrarCita("Juan Perez", LocalDate.now().plusDays(30), LocalTime.of(10, 0), "Corte");

        venta ventaRegistrada = new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("100.00"), new BigDecimal("50.00"));
        repoVentas.registrarVentaTransaccional(ventaRegistrada);

        cita enBase = repoCitas.obtenerPorId(citaPrueba.getIdCita());
        assertEquals("Completada", enBase.getEstado());

        venta persistida = repoVentas.obtenerPorCita(citaPrueba.getIdCita());
        assertNotNull(persistida);
        assertTrue("La venta debe tener un identificador real en la base de datos.", persistida.getIdVenta() > 0);
        assertEquals(new BigDecimal("50.00"), persistida.getTotal());
        assertEquals(new BigDecimal("100.00"), persistida.getEfectivo());
        assertEquals(new BigDecimal("50.00"), persistida.getCambio());
        assertNotNull(persistida.getCita());
        assertEquals("Juan Perez", persistida.getCita().getNombreCliente());
    }

    @Test
    public void unaCitaSoloPuedeLiquidarseUnaVez() {
        cita citaPrueba = registrarCita("Ana Lopez", LocalDate.now().plusDays(31), LocalTime.of(11, 0), "Corteclasico");

        venta ventaRegistrada = new venta(citaPrueba, "Corte clasico ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2));
        repoVentas.registrarVentaTransaccional(ventaRegistrada);

        try {
            repoVentas.registrarVentaTransaccional(ventaRegistrada);
            fail("Debe impedirse el doble cobro de una misma cita.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("ya fue cobrada"));
        }
    }

    @Test
    public void noSePuedeCobrarUnaCitaCancelada() {
        cita citaPrueba = registrarCita("Carlos Ruiz", LocalDate.now().plusDays(32), LocalTime.of(12, 0), "Cortepremium");
        repoCitas.actualizarEstado(citaPrueba.getIdCita(), "Cancelada");

        try {
            repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                    new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2)));
            fail("Debe impedirse el cobro de una cita cancelada.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("no es cobrable") || e.getMessage().contains("ya fue cobrada"));
        }
    }

    @Test
    public void unaVentaNoAceptaMontosInvalidos() {
        cita citaPrueba = registrarCita("Luis Diaz", LocalDate.now().plusDays(33), LocalTime.of(13, 0), "Corte");

        try {
            repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", BigDecimal.ZERO.setScale(2),
                    new BigDecimal("50.00"), new BigDecimal("50.00")));
            fail("El total debe ser mayor a cero.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mayor a cero"));
        }

        try {
            repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                    new BigDecimal("10.00"), new BigDecimal("-40.00")));
            fail("El efectivo debe cubrir el total.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("efectivo"));
        }
    }

    @Test
    public void listarTodasDevuelveLasVentasRegistradas() {
        assertTrue(repoVentas.listarTodas().isEmpty());

        cita citaPrueba = registrarCita("Sofia Vega", LocalDate.now().plusDays(34), LocalTime.of(14, 0), "Corte");
        repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("60.00"), new BigDecimal("10.00")));

        List<venta> ventas = repoVentas.listarTodas();
        assertEquals(1, ventas.size());
        assertEquals("Sofia Vega", ventas.get(0).getCita().getNombreCliente());
    }

    @Test
    public void listarPorRangoFechasFiltraElHistorico() {
        LocalDate hoy = LocalDate.now();
        cita citaPrueba = registrarCita("Pedro Gil", hoy.plusDays(40), LocalTime.of(15, 0), "Corte");
        repoVentas.registrarVentaTransaccional(new venta(0, citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2), LocalDateTime.now()));

        assertEquals(1, repoVentas.listarPorRangoFechas(hoy.minusDays(1), hoy.plusDays(1)).size());
        assertEquals(0, repoVentas.listarPorRangoFechas(hoy.minusDays(10), hoy.minusDays(5)).size());
        assertEquals(1, repoVentas.listarPorRangoFechas(null, null).size());
    }

    @Test
    public void listarPorPeriodoAgrupaElMesCorrecto() {
        LocalDate hoy = LocalDate.now();
        YearMonth periodoActual = YearMonth.from(hoy);
        YearMonth periodoAnterior = periodoActual.minusMonths(1);

        cita citaPrueba = registrarCita("Marta Ruiz", hoy.plusDays(45), LocalTime.of(16, 0), "Corte");
        repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2)));

        assertEquals(1, repoVentas.listarPorPeriodo(periodoActual).size());
        assertEquals(0, repoVentas.listarPorPeriodo(periodoAnterior).size());
    }

    @Test
    public void obtenerPorCitaSinVentaDevuelveNulo() {
        assertNull(repoVentas.obtenerPorCita(999));
    }

    @Test
    public void lasVentasRegistradasSonInmutablesEnElMotor() throws SQLException {
        cita citaPrueba = registrarCita("Rosa Nunez", LocalDate.now().plusDays(46), LocalTime.of(17, 0), "Corte");
        repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2)));

        Connection conn = db.obtenerConexion();
        try (PreparedStatement ps = conn.prepareStatement("UPDATE ventas SET total = '1.00' WHERE id_cita = ?")) {
            ps.setInt(1, citaPrueba.getIdCita());
            ps.executeUpdate();
            fail("Las ventas no deben ser modificables.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("inmutables"));
        }

        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ventas WHERE id_cita = ?")) {
            ps.setInt(1, citaPrueba.getIdCita());
            ps.executeUpdate();
            fail("Las ventas no deben ser eliminables.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("eliminar"));
        }
    }

    @Test
    public void unaCitaCompletadaNoPuedeVolverASuEstadoActivo() throws SQLException {
        cita citaPrueba = registrarCita("Javier Solis", LocalDate.now().plusDays(47), LocalTime.of(18, 0), "Corte");
        repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2)));

        Connection conn = db.obtenerConexion();
        try (PreparedStatement ps = conn.prepareStatement("UPDATE citas SET estado = 'Pendiente' WHERE id_cita = ?")) {
            ps.setInt(1, citaPrueba.getIdCita());
            ps.executeUpdate();
            fail("Una cita completada no debe volver a un estado activo.");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("completada"));
        }
    }

    @Test
    public void elSnapshotHistoricoSeConservaEnLaVenta() throws SQLException {
        cita citaPrueba = registrarCita("Diego Paz", LocalDate.now().plusDays(48), LocalTime.of(19, 0), "Corte");
        repoVentas.registrarVentaTransaccional(new venta(citaPrueba, "Corte ($50.00)", new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO.setScale(2)));

        // Se edita el precio del catalogo: la venta historica no debe alterarse (RNF 4).
        List<servicios> servicios = repoServicios.listarTodos();
        servicios.get(0).setCostoUnitario(new BigDecimal("999.00"));
        repoServicios.actualizar(servicios.get(0));

        venta persistida = repoVentas.obtenerPorCita(citaPrueba.getIdCita());
        assertEquals("Corte ($50.00)", persistida.getSnapshotServicios());
        assertEquals(new BigDecimal("50.00"), persistida.getTotal());

        Connection conn = db.obtenerConexion();
        try (PreparedStatement ps = conn.prepareStatement("SELECT snapshot_servicios, total FROM ventas WHERE id_cita = ?")) {
            ps.setInt(1, citaPrueba.getIdCita());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Corte ($50.00)", rs.getString("snapshot_servicios"));
                assertEquals("50.00", rs.getString("total"));
            }
        }
        assertFalse(repoVentas.listarTodas().isEmpty());
    }
}
