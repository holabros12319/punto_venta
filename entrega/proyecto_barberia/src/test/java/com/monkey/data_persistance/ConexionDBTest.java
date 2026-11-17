package com.monkey.data_persistance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.monkey.model.cita;
import com.monkey.model.servicios;

/**
 * Capa 3 - Pruebas del esquema y de la conexion SQLite.
 * Verifica las restricciones del modelo relacional: indice unico parcial,
 * categorias cerradas, borrado restringido y disparadores de inmutabilidad.
 */
public class ConexionDBTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    private boolean existeObjetoSqlite(String nombre) throws SQLException {
        Connection conn = db.obtenerConexion();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT name FROM sqlite_master WHERE name = '" + nombre + "'")) {
            return rs.next();
        }
    }

    @Test
    public void inicializarTablasCreaElEsquemaCompleto() throws SQLException {
        assertTrue(existeObjetoSqlite("servicios"));
        assertTrue(existeObjetoSqlite("citas"));
        assertTrue(existeObjetoSqlite("cita_servicios"));
        assertTrue(existeObjetoSqlite("ventas"));
        assertTrue(existeObjetoSqlite("idx_cita_horario_activo"));
        assertTrue(existeObjetoSqlite("trg_validar_cita_cobrable"));
        assertTrue(existeObjetoSqlite("trg_bloquear_update_ventas"));
        assertTrue(existeObjetoSqlite("trg_bloquear_delete_ventas"));
        assertTrue(existeObjetoSqlite("trg_bloquear_cita_completada"));
    }

    @Test
    public void migraCitasExistentesCalculandoUnaHoraDeTermino() throws SQLException {
        conexion_DB baseAnterior = new conexion_DB("jdbc:sqlite::memory:");
        try {
            Connection conn = baseAnterior.obtenerConexion();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE citas (id_cita INTEGER PRIMARY KEY, nombre_cliente TEXT NOT NULL, "
                        + "fecha TEXT NOT NULL, horario TEXT NOT NULL, estado TEXT NOT NULL)");
                stmt.execute("INSERT INTO citas VALUES (1, 'Ana', '2026-12-20', '10:30', 'Completada')");
                stmt.execute("CREATE TRIGGER trg_bloquear_cita_completada BEFORE UPDATE ON citas "
                        + "WHEN OLD.estado = 'Completada' BEGIN "
                        + "SELECT RAISE(ABORT, 'Error: Una cita completada no puede ser modificada.'); END");
            }

            baseAnterior.inicializarTablas();
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT hora_fin FROM citas WHERE id_cita = 1")) {
                assertTrue(rs.next());
                assertEquals("11:30", rs.getString("hora_fin"));
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE citas SET nombre_cliente = 'Ana editada' WHERE id_cita = 1");
                org.junit.Assert.fail("El trigger debe seguir bloqueando cambios tras la migración.");
            } catch (SQLException esperada) {
                assertTrue(esperada.getMessage().contains("no puede ser modificada"));
            }
        } finally {
            baseAnterior.cerrarConexion();
        }
    }

    @Test
    public void completaTerminosNulosEnBasesConLaColumnaYaCreada() throws SQLException {
        conexion_DB baseParcial = new conexion_DB("jdbc:sqlite::memory:");
        try {
            Connection conn = baseParcial.obtenerConexion();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE citas (id_cita INTEGER PRIMARY KEY, nombre_cliente TEXT NOT NULL, "
                        + "fecha TEXT NOT NULL, horario TEXT NOT NULL, hora_fin TEXT, estado TEXT NOT NULL)");
                stmt.execute("INSERT INTO citas VALUES (1, 'Ana', '2026-12-20', '10:30', NULL, 'Confirmada')");
            }

            baseParcial.inicializarTablas();
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT hora_fin FROM citas WHERE id_cita = 1")) {
                assertTrue(rs.next());
                assertEquals("11:30", rs.getString("hora_fin"));
            }
        } finally {
            baseParcial.cerrarConexion();
        }
    }

    @Test
    public void elIndiceUnicoParcialImpideEmpalmarHorarios() {
        servicios servicio = new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo");
        repoServicios.insertar(servicio);
        List<servicios> guardados = repoServicios.listarTodos();

        List<servicios> serviciosCita = new ArrayList<>();
        serviciosCita.add(guardados.get(0));

        LocalDate fecha = LocalDate.now().plusDays(60);
        repoCitas.insertar(new cita("Ana", fecha, LocalTime.of(11, 0), serviciosCita));

        try {
            repoCitas.insertar(new cita("Luis", fecha, LocalTime.of(11, 0), serviciosCita));
            org.junit.Assert.fail("No se deben permitir dos citas activas en el mismo horario.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("ocupado"));
        }
    }

    @Test
    public void cancelarUnaCitaLiberaElHorario() {
        servicios servicio = new servicios("Barba", new BigDecimal("30.00"), "Otros servicios");
        repoServicios.insertar(servicio);
        List<servicios> guardados = repoServicios.listarTodos();

        List<servicios> serviciosCita = new ArrayList<>();
        serviciosCita.add(guardados.get(0));

        LocalDate fecha = LocalDate.now().plusDays(61);
        cita primera = new cita("Ana", fecha, LocalTime.of(12, 0), serviciosCita);
        repoCitas.insertar(primera);
        repoCitas.actualizarEstado(primera.getIdCita(), "Cancelada");

        // Con la cita cancelada el mismo horario vuelve a estar disponible (RF 12).
        cita segunda = new cita("Luis", fecha, LocalTime.of(12, 0), serviciosCita);
        repoCitas.insertar(segunda);
        assertTrue(segunda.getIdCita() > 0);
    }

    @Test
    public void elCatalogoRechazaCategoriasFueraDeLasCerradas() {
        try {
            repoServicios.insertar(new servicios("Servicio raro", new BigDecimal("10.00"), "Categoria inventada"));
            org.junit.Assert.fail("Solo se admiten las dos categorias fijas.");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Error al guardar el servicio"));
        }
    }

    @Test
    public void elCatalogoRechazaNombresDuplicados() {
        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
        try {
            repoServicios.insertar(new servicios("corte", new BigDecimal("60.00"), "Cortes de pelo"));
            org.junit.Assert.fail("No se admiten nombres duplicados sin distinguir mayusculas.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Ya existe"));
        }
    }

    @Test
    public void elCatalogoRechazaPreciosNoPositivos() {
        try {
            repoServicios.insertar(new servicios("Corte gratis", BigDecimal.ZERO.setScale(2), "Cortes de pelo"));
            org.junit.Assert.fail("El precio debe ser mayor a cero.");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Error al guardar el servicio"));
        }
    }

    @Test
    public void losServiciosNoSePuedenEliminarSiEstanLigados() throws SQLException {
        servicios servicio = new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo");
        repoServicios.insertar(servicio);
        List<servicios> guardados = repoServicios.listarTodos();

        List<servicios> serviciosCita = new ArrayList<>();
        serviciosCita.add(guardados.get(0));
        repoCitas.insertar(new cita("Ana", LocalDate.now().plusDays(62), LocalTime.of(9, 0), serviciosCita));

        Connection conn = db.obtenerConexion();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM servicios WHERE id_servicio = " + guardados.get(0).getIdServicio());
            org.junit.Assert.fail("Un servicio ligado a citas no debe eliminarse.");
        } catch (SQLException e) {
            assertTrue(true);
        }
    }

    @Test
    public void listarTodosYListarActivosRespetanElEstado() {
        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));

        List<servicios> guardados = repoServicios.listarTodos();
        guardados.get(0).setActivo(false);
        repoServicios.actualizar(guardados.get(0));

        assertEquals(1, repoServicios.listarTodos().size());
        assertTrue(repoServicios.listarActivos().isEmpty());
        assertFalse(repoServicios.listarTodos().get(0).isActivo());
    }
}
