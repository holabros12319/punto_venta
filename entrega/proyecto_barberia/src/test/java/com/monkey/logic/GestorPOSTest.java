package com.monkey.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.model.cita;
import com.monkey.model.servicios;
import com.monkey.model.venta;

/**
 * Capa 2 - Pruebas del gestor de punto de venta.
 * Verifica la importacion de citas cobrables (RF 14), el calculo del cambio
 * (RF 15) y el registro transaccional con revalidacion fresca (RF 16).
 */
public class GestorPOSTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;
    private RepositorioVentas repoVentas;
    private GestorPOS gestorPOS;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
        repoVentas = new RepositorioVentas(db, repoCitas);
        gestorPOS = new GestorPOS(repoCitas, repoVentas);

        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
        repoServicios.insertar(new servicios("Barba", new BigDecimal("30.00"), "Otros servicios"));
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    private cita registrarCita(String cliente, LocalDate fecha, LocalTime hora) {
        List<servicios> serviciosCita = new ArrayList<>(repoServicios.listarActivos());
        cita nueva = new cita(cliente, fecha, hora, serviciosCita);
        repoCitas.insertar(nueva);
        return nueva;
    }

    @Test
    public void calcularCambioDevuelveLaDiferenciaExacta() {
        assertEquals(new BigDecimal("50.00"), gestorPOS.calcularCambio(new BigDecimal("50.00"), new BigDecimal("100.00")));
        assertEquals(new BigDecimal("0.00"), gestorPOS.calcularCambio(new BigDecimal("50.00"), new BigDecimal("50.00")));
    }

    @Test
    public void calcularCambioRechazaMontosInvalidos() {
        try {
            gestorPOS.calcularCambio(BigDecimal.ZERO.setScale(2), new BigDecimal("10.00"));
            fail("El total debe ser mayor a cero.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mayor a cero"));
        }

        try {
            gestorPOS.calcularCambio(new BigDecimal("50.00"), new BigDecimal("20.00"));
            fail("El efectivo debe cubrir el total.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("no cubre"));
        }
    }

    @Test
    public void buscarCitasPorCobrarSoloDevuelveActivas() {
        LocalDate fecha = LocalDate.now().plusDays(20);
        cita pendiente = registrarCita("Ana Torres", fecha, LocalTime.of(9, 0));
        registrarCita("Luis Torres", fecha, LocalTime.of(10, 0));
        cita cancelada = registrarCita("Carlos Paz", fecha, LocalTime.of(11, 0));
        repoCitas.actualizarEstado(cancelada.getIdCita(), "Cancelada");

        List<cita> cobrables = gestorPOS.buscarCitasPorCobrar("");
        assertEquals(2, cobrables.size());
        assertEquals(2, gestorPOS.buscarCitasPorCobrar("torres").size());
        assertEquals(1, gestorPOS.buscarCitasPorCobrar("ana").size());
        assertFalse("Una cita cancelada no es cobrable.", contieneCita(cobrables, cancelada.getIdCita()));
        assertTrue("Una cita pendiente debe ser cobrable.", contieneCita(cobrables, pendiente.getIdCita()));
    }

    private boolean contieneCita(List<cita> citas, int idCita) {
        for (cita item : citas) {
            if (item.getIdCita() == idCita) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void procesarCobroRegistraLaVentaYCompletaLaCita() {
        cita guardada = registrarCita("Ana Torres", LocalDate.now().plusDays(21), LocalTime.of(9, 0));

        venta ventaRegistrada = gestorPOS.procesarCobro(guardada.getIdCita(), new BigDecimal("200.00"));

        assertNotNull(ventaRegistrada);
        assertEquals(new BigDecimal("80.00"), ventaRegistrada.getTotal());
        assertEquals(new BigDecimal("200.00"), ventaRegistrada.getEfectivo());
        assertEquals(new BigDecimal("120.00"), ventaRegistrada.getCambio());
        assertTrue(ventaRegistrada.getSnapshotServicios().contains("Corte"));
        assertTrue(ventaRegistrada.getSnapshotServicios().contains("Barba"));

        assertEquals("Completada", repoCitas.obtenerPorId(guardada.getIdCita()).getEstado());

        venta persistida = gestorPOS.obtenerVentaRegistrada(guardada.getIdCita());
        assertNotNull(persistida);
        assertTrue(persistida.getIdVenta() > 0);
    }

    @Test
    public void procesarCobroRevalidaElEstadoContraLaBaseDeDatos() {
        cita guardada = registrarCita("Ana Torres", LocalDate.now().plusDays(22), LocalTime.of(9, 0));
        repoCitas.actualizarEstado(guardada.getIdCita(), "Cancelada");

        try {
            gestorPOS.procesarCobro(guardada.getIdCita(), new BigDecimal("200.00"));
            fail("Una cita cancelada no es cobrable.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("no es cobrable"));
        }
    }

    @Test
    public void procesarCobroNoPermiteCobrarDosVecesLaMismaCita() {
        cita guardada = registrarCita("Ana Torres", LocalDate.now().plusDays(23), LocalTime.of(9, 0));

        gestorPOS.procesarCobro(guardada.getIdCita(), new BigDecimal("200.00"));

        try {
            gestorPOS.procesarCobro(guardada.getIdCita(), new BigDecimal("200.00"));
            fail("Una cita genera como maximo una venta.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("no es cobrable"));
        }
    }

    @Test
    public void procesarCobroRechazaEfectivoInsuficiente() {
        cita guardada = registrarCita("Ana Torres", LocalDate.now().plusDays(24), LocalTime.of(9, 0));

        try {
            gestorPOS.procesarCobro(guardada.getIdCita(), new BigDecimal("10.00"));
            fail("El efectivo debe cubrir el total.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("no cubre"));
        }

        assertEquals("Pendiente", repoCitas.obtenerPorId(guardada.getIdCita()).getEstado());
    }

    @Test
    public void procesarCobroExigeUnaCitaExistente() {
        try {
            gestorPOS.procesarCobro(9999, new BigDecimal("100.00"));
            fail("La cita debe existir.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No se encontro"));
        }
    }
}
