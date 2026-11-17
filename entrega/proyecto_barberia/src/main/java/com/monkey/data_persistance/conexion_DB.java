package com.monkey.data_persistance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class conexion_DB {

    private final String urlConexion;
    private Connection conexionActiva;

    public conexion_DB(String urlConexion) {
        this.urlConexion = urlConexion;
    }

    public Connection obtenerConexion() throws SQLException {
        if (conexionActiva == null || conexionActiva.isClosed()) {
            conexionActiva = DriverManager.getConnection(urlConexion);
            configurarPragmas(conexionActiva);
        }
        return conexionActiva;
    }

    private void configurarPragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
    }

    public void inicializarTablas() {
        String sqlServicios = """
            CREATE TABLE IF NOT EXISTS servicios (
                id_servicio INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(trim(nombre)) > 0),
                costo_unitario TEXT NOT NULL CHECK (CAST(costo_unitario AS REAL) > 0),
                categoria TEXT NOT NULL CHECK (categoria IN ('Cortes de pelo', 'Otros servicios')),
                activo INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0, 1))
            );
            """;

        String sqlCitas = """
            CREATE TABLE IF NOT EXISTS citas (
                id_cita INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre_cliente TEXT NOT NULL CHECK (length(trim(nombre_cliente)) > 0),
                fecha TEXT NOT NULL,
                horario TEXT NOT NULL,
                hora_fin TEXT NOT NULL,
                estado TEXT NOT NULL DEFAULT 'Pendiente'
                    CHECK (estado IN ('Pendiente', 'Confirmada', 'Cancelada', 'Completada'))
            );
            """;

        String sqlIndiceHorario = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_cita_horario_activo
            ON citas(fecha, horario)
            WHERE estado IN ('Pendiente', 'Confirmada');
            """;

        String sqlCitaServicios = """
            CREATE TABLE IF NOT EXISTS cita_servicios (
                id_detalle INTEGER PRIMARY KEY AUTOINCREMENT,
                id_cita INTEGER NOT NULL,
                id_servicio INTEGER NOT NULL,
                FOREIGN KEY (id_cita) REFERENCES citas(id_cita) ON DELETE RESTRICT,
                FOREIGN KEY (id_servicio) REFERENCES servicios(id_servicio) ON DELETE RESTRICT
            );
            """;

        String sqlVentas = """
            CREATE TABLE IF NOT EXISTS ventas (
                id_venta INTEGER PRIMARY KEY AUTOINCREMENT,
                id_cita INTEGER NOT NULL UNIQUE,
                snapshot_servicios TEXT NOT NULL,
                total TEXT NOT NULL CHECK (CAST(total AS REAL) > 0),
                efectivo TEXT NOT NULL CHECK (CAST(efectivo AS REAL) >= CAST(total AS REAL)),
                cambio TEXT NOT NULL CHECK (CAST(cambio AS REAL) >= 0),
                fecha_hora TEXT NOT NULL,
                FOREIGN KEY (id_cita) REFERENCES citas(id_cita) ON DELETE RESTRICT
            );
            """;

        String triggerValidarCitaCobrable = """
            CREATE TRIGGER IF NOT EXISTS trg_validar_cita_cobrable
            BEFORE INSERT ON ventas
            BEGIN
                SELECT RAISE(ABORT, 'Error: Solo se pueden cobrar citas en estado Pendiente o Confirmada.')
                WHERE (SELECT estado FROM citas WHERE id_cita = NEW.id_cita) NOT IN ('Pendiente', 'Confirmada');
            END;
            """;

        String triggerInmutabilidadVentasUpdate = """
            CREATE TRIGGER IF NOT EXISTS trg_bloquear_update_ventas
            BEFORE UPDATE ON ventas
            BEGIN
                SELECT RAISE(ABORT, 'Error contable: Las ventas registradas son inmutables.');
            END;
            """;

        String triggerInmutabilidadVentasDelete = """
            CREATE TRIGGER IF NOT EXISTS trg_bloquear_delete_ventas
            BEFORE DELETE ON ventas
            BEGIN
                SELECT RAISE(ABORT, 'Error contable: No se permite eliminar ventas.');
            END;
            """;

        String triggerBloquearCitaCompletada = """
            CREATE TRIGGER IF NOT EXISTS trg_bloquear_cita_completada
            BEFORE UPDATE ON citas
            WHEN OLD.estado = 'Completada'
            BEGIN
                SELECT RAISE(ABORT, 'Error: Una cita completada no puede ser modificada.');
            END;
            """;

        try {
            Connection conn = obtenerConexion();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sqlServicios);
                stmt.execute(sqlCitas);
                agregarColumnaHoraFinSiNoExiste(conn);
                stmt.execute(sqlIndiceHorario);
                stmt.execute(sqlCitaServicios);
                stmt.execute(sqlVentas);
                stmt.execute(triggerValidarCitaCobrable);
                stmt.execute(triggerInmutabilidadVentasUpdate);
                stmt.execute(triggerInmutabilidadVentasDelete);
                stmt.execute(triggerBloquearCitaCompletada);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al inicializar la base de datos: " + e.getMessage(), e);
        }
    }

    private void agregarColumnaHoraFinSiNoExiste(Connection conn) throws SQLException {
        boolean existeHoraFin = false;
        try (Statement stmt = conn.createStatement();
                java.sql.ResultSet columnas = stmt.executeQuery("PRAGMA table_info(citas)")) {
            while (columnas.next()) {
                if ("hora_fin".equalsIgnoreCase(columnas.getString("name"))) {
                    existeHoraFin = true;
                    break;
                }
            }
        }

        if (!existeHoraFin) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE citas ADD COLUMN hora_fin TEXT");
            }
        }

        boolean autoCommitAnterior = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            // El trigger histórico bloquea cualquier UPDATE sobre citas completadas.
            // Se suspende solo dentro de esta migración y se restaura en la misma transacción.
            stmt.execute("DROP TRIGGER IF EXISTS trg_bloquear_cita_completada");
            stmt.execute("UPDATE citas SET hora_fin = substr(time(horario, '+1 hour'), 1, 5) WHERE hora_fin IS NULL");
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS trg_bloquear_cita_completada
                BEFORE UPDATE ON citas
                WHEN OLD.estado = 'Completada'
                BEGIN
                    SELECT RAISE(ABORT, 'Error: Una cita completada no puede ser modificada.');
                END;
                """);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitAnterior);
        }
    }

    public void cerrarConexion() {
        try {
            if (conexionActiva != null && !conexionActiva.isClosed()) {
                conexionActiva.close();
            }
        } catch (SQLException e) {
            System.err.println("Aviso al cerrar SQLite: " + e.getMessage());
        }
    }
}
