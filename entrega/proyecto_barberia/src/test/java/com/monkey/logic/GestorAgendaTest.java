package com.monkey.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.monkey.data_persistance.conexion_DB;
import com.monkey.model.cita;
import com.monkey.model.servicios;

/**
 * Capa 2 - Pruebas del gestor de agenda.
 * Verifica los horarios disponibles (RF 8), el registro de citas (RF 9 y RF 10),
 * el ciclo de estados (RF 11 y RF 12) y la busqueda por cliente (RF 13).
 */
public class GestorAgendaTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;
    private GestorAgenda gestorAgenda;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
        gestorAgenda = new GestorAgenda(repoCitas);

        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    private List<servicios> serviciosActivos() {
        return repoServicios.listarActivos();
    }

    private cita registrarCita(String cliente, LocalDate fecha, LocalTime hora) {
        List<servicios> serviciosCita = new ArrayList<>(serviciosActivos());
        cita nueva = new cita(cliente, fecha, hora, serviciosCita);
        gestorAgenda.registrarCita(nueva);
        return nueva;
    }

    @Test
    public void laJornadaLaboralAbreDe09a19Horas() {
        List<LocalTime> disponibles = gestorAgenda.obtenerHorariosDisponibles(LocalDate.now().plusDays(30));

        assertEquals(11, disponibles.size());
        assertEquals(LocalTime.of(9, 0), disponibles.get(0));
        assertEquals(LocalTime.of(19, 0), disponibles.get(10));
    }

    @Test
    public void unHorarioOcupadoDesapareceDeLosDisponibles() {
        LocalDate fecha = LocalDate.now().plusDays(31);
        registrarCita("Ana", fecha, LocalTime.of(11, 0));

        List<LocalTime> disponibles = gestorAgenda.obtenerHorariosDisponibles(fecha);
        assertEquals(10, disponibles.size());
        assertFalse(disponibles.contains(LocalTime.of(11, 0)));
    }

    @Test
    public void enElDiaActualSeDescartanLasHorasYaPasadas() {
        List<LocalTime> disponibles = gestorAgenda.obtenerHorariosDisponibles(LocalDate.now());

        LocalTime ahora = LocalTime.now();
        for (LocalTime horario : disponibles) {
            assertFalse("No debe ofrecerse un horario ya pasado: " + horario, horario.isBefore(ahora));
        }
        assertTrue(disponibles.size() <= 11);
    }

    @Test
    public void registrarCitaGuardaElEstadoPendiente() {
        cita guardada = registrarCita("Juan Perez", LocalDate.now().plusDays(32), LocalTime.of(10, 0));

        assertTrue(guardada.getIdCita() > 0);
        assertEquals("Pendiente", guardada.getEstado());
        assertEquals(1, repoCitas.obtenerPorId(guardada.getIdCita()).getServicios().size());
    }

    @Test
    public void noSeAdmitenCitasSinClienteNiSinServicios() {
        try {
            gestorAgenda.registrarCita(new cita("   ", LocalDate.now().plusDays(33), LocalTime.of(10, 0),
                    serviciosActivos()));
            fail("El cliente no puede estar vacio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cliente"));
        }

        try {
            gestorAgenda.registrarCita(new cita("Ana", LocalDate.now().plusDays(33), LocalTime.of(10, 0),
                    new ArrayList<servicios>()));
            fail("La cita debe tener al menos un servicio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("servicio"));
        }
    }

    @Test
    public void noSeAdmitenCitasEnElPasado() {
        try {
            gestorAgenda.registrarCita(new cita("Ana", LocalDate.now().minusDays(1), LocalTime.of(10, 0),
                    serviciosActivos()));
            fail("No se pueden registrar citas en el pasado.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("anterior"));
        }
    }

    @Test
    public void noSePuedeEmpalmarUnHorarioOcupado() {
        LocalDate fecha = LocalDate.now().plusDays(34);
        registrarCita("Ana", fecha, LocalTime.of(13, 0));

        try {
            registrarCita("Luis", fecha, LocalTime.of(13, 0));
            fail("No se deben permitir dos citas activas en el mismo horario.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("ocupado"));
        }
    }

    @Test
    public void cambiarEstadoAConfirmadaActualizaLaCita() {
        cita guardada = registrarCita("Ana", LocalDate.now().plusDays(35), LocalTime.of(14, 0));

        gestorAgenda.cambiarEstadoCita(guardada.getIdCita(), "Confirmada");
        assertEquals("Confirmada", repoCitas.obtenerPorId(guardada.getIdCita()).getEstado());
    }

    @Test
    public void cancelarLiberaElHorarioYEsDefinitivo() {
        LocalDate fecha = LocalDate.now().plusDays(36);
        cita guardada = registrarCita("Ana", fecha, LocalTime.of(15, 0));

        gestorAgenda.cambiarEstadoCita(guardada.getIdCita(), "Cancelada");
        assertEquals("Cancelada", repoCitas.obtenerPorId(guardada.getIdCita()).getEstado());

        assertTrue(gestorAgenda.obtenerHorariosDisponibles(fecha).contains(LocalTime.of(15, 0)));

        try {
            gestorAgenda.cambiarEstadoCita(guardada.getIdCita(), "Confirmada");
            fail("Una cita cancelada no puede volver a un estado activo.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cancelada"));
        }
    }

    @Test
    public void elEstadoCompletadaNoEsSeleccionableManualmente() {
        cita guardada = registrarCita("Ana", LocalDate.now().plusDays(37), LocalTime.of(16, 0));

        try {
            gestorAgenda.cambiarEstadoCita(guardada.getIdCita(), "Completada");
            fail("Completada solo se asigna al cobrar en el punto de venta.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("no es valido"));
        }
    }

    @Test
    public void buscarCitasFiltraPorCoincidenciaParcial() {
        registrarCita("Ana Torres", LocalDate.now().plusDays(38), LocalTime.of(9, 0));
        registrarCita("Luis Torres", LocalDate.now().plusDays(39), LocalTime.of(9, 0));
        registrarCita("Maria Gomez", LocalDate.now().plusDays(40), LocalTime.of(9, 0));

        assertEquals(2, gestorAgenda.buscarCitas("torres", null).size());
        assertEquals(1, gestorAgenda.buscarCitas("torres", LocalDate.now().plusDays(39)).size());
        assertEquals(3, gestorAgenda.buscarCitas("", null).size());
    }

    @Test
    public void elGestorExigeUnRepositorioDeCitas() {
        try {
            new GestorAgenda(null);
            fail("El repositorio de citas es obligatorio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatorio"));
        }
    }
}
