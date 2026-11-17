package com.monkey.data_persistance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.monkey.model.cita;
import com.monkey.model.servicios;

public class RepositorioCitas {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private final conexion_DB conexionSQL;

    public RepositorioCitas(conexion_DB conexionSQL) {
        this.conexionSQL = conexionSQL;
    }

    public void insertar(cita cita) {
        String sqlCita = "INSERT INTO citas (nombre_cliente, fecha, horario, hora_fin, estado) VALUES (?, ?, ?, ?, ?)";
        String sqlDetalle = "INSERT INTO cita_servicios (id_cita, id_servicio) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = conexionSQL.obtenerConexion();
            conn.setAutoCommit(false);

            int idCitaGenerado;
            try (PreparedStatement psCita = conn.prepareStatement(sqlCita, Statement.RETURN_GENERATED_KEYS)) {
                psCita.setString(1, cita.getNombreCliente().trim());
                psCita.setString(2, cita.getFecha().toString());
                psCita.setString(3, cita.getHora().format(FORMATO_HORA));
                psCita.setString(4, cita.getHoraFin().format(FORMATO_HORA));
                psCita.setString(5, cita.getEstado());
                psCita.executeUpdate();

                try (ResultSet keys = psCita.getGeneratedKeys()) {
                    if (keys.next()) {
                        idCitaGenerado = keys.getInt(1);
                        cita.setIdCita(idCitaGenerado);
                    } else {
                        throw new SQLException("No se pudo obtener el ID generado para la cita.");
                    }
                }
            }

            try (PreparedStatement psDetalle = conn.prepareStatement(sqlDetalle)) {
                for (servicios servicio : cita.getServicios()) {
                    psDetalle.setInt(1, idCitaGenerado);
                    psDetalle.setInt(2, servicio.getIdServicio());
                    psDetalle.addBatch();
                }
                psDetalle.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error al revertir transacción de cita: " + ex.getMessage());
                }
            }
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                throw new IllegalStateException("El horario seleccionado ya está ocupado para esa fecha.", e);
            }
            throw new RuntimeException("Error al registrar la cita en SQL: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    System.err.println("Aviso al restaurar autoCommit: " + e.getMessage());
                }
            }
        }
    }

    public cita obtenerPorId(int idCita) {
        String sql = "SELECT id_cita, nombre_cliente, fecha, horario, hora_fin, estado FROM citas WHERE id_cita = ?";

        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idCita);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return construirCitaDesdeFila(rs, conn);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar la cita por ID: " + e.getMessage(), e);
        }
        return null;
    }

    public List<LocalTime> obtenerHorariosOcupados(LocalDate fecha) {
        String sql = "SELECT horario FROM citas WHERE fecha = ? AND estado IN ('Pendiente', 'Confirmada')";
        List<LocalTime> ocupados = new ArrayList<>();

        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, fecha.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ocupados.add(LocalTime.parse(rs.getString("horario"), FORMATO_HORA));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar horarios ocupados: " + e.getMessage(), e);
        }
        return ocupados;
    }

    public List<cita> buscarPorFiltro(String cliente, LocalDate fecha) {
        StringBuilder sql = new StringBuilder(
            "SELECT id_cita, nombre_cliente, fecha, horario, hora_fin, estado FROM citas WHERE 1=1"
        );
        boolean filtrarCliente = (cliente != null && !cliente.trim().isEmpty());
        boolean filtrarFecha = (fecha != null);

        if (filtrarCliente) {
            sql.append(" AND LOWER(nombre_cliente) LIKE ? ESCAPE '\\'");
        }
        if (filtrarFecha) {
            sql.append(" AND fecha = ?");
        }
        sql.append(" ORDER BY fecha DESC, horario ASC");

        List<cita> resultados = new ArrayList<>();
        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int indice = 1;
                if (filtrarCliente) {
                    ps.setString(indice++, "%" + escaparLike(cliente.trim().toLowerCase()) + "%");
                }
                if (filtrarFecha) {
                    ps.setString(indice, fecha.toString());
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        resultados.add(construirCitaDesdeFila(rs, conn));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar citas en SQL: " + e.getMessage(), e);
        }
        return resultados;
    }

    public List<cita> listarCobrables(String cliente) {
        StringBuilder sql = new StringBuilder(
            "SELECT id_cita, nombre_cliente, fecha, horario, hora_fin, estado FROM citas WHERE estado IN ('Pendiente', 'Confirmada')"
        );
        boolean filtrarCliente = (cliente != null && !cliente.trim().isEmpty());

        if (filtrarCliente) {
            sql.append(" AND LOWER(nombre_cliente) LIKE ? ESCAPE '\\'");
        }
        sql.append(" ORDER BY fecha ASC, horario ASC");

        List<cita> cobrables = new ArrayList<>();
        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (filtrarCliente) {
                    ps.setString(1, "%" + escaparLike(cliente.trim().toLowerCase()) + "%");
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cobrables.add(construirCitaDesdeFila(rs, conn));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar citas cobrables: " + e.getMessage(), e);
        }
        return cobrables;
    }

    // Sobrecarga para uso directo de GestorAgenda (Capa 2)
    public void actualizarEstado(int idCita, String estado) {
        try {
            Connection conn = conexionSQL.obtenerConexion();
            actualizarEstado(idCita, estado, conn);
        } catch (SQLException e) {
            throw new RuntimeException("Error de conexión al actualizar estado de cita: " + e.getMessage(), e);
        }
    }

    // Sobrecarga transaccional usada por RepositorioVentas (Capa 3)
    public void actualizarEstado(int idCita, String estado, Connection conn) throws SQLException {
        String sql = "UPDATE citas SET estado = ? WHERE id_cita = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setInt(2, idCita);

            int filasAfectadas = ps.executeUpdate();
            if (filasAfectadas == 0) {
                throw new IllegalArgumentException("No se encontró la cita con ID: " + idCita);
            }
        }
    }

    private cita construirCitaDesdeFila(ResultSet rs, Connection conn) throws SQLException {
        int idCita = rs.getInt("id_cita");
        String nombreCliente = rs.getString("nombre_cliente");
        LocalDate fecha = LocalDate.parse(rs.getString("fecha"));
        LocalTime horario = LocalTime.parse(rs.getString("horario"), FORMATO_HORA);
        LocalTime horaFin = LocalTime.parse(rs.getString("hora_fin"), FORMATO_HORA);
        String estado = rs.getString("estado");
        List<servicios> servicios = cargarServiciosDeCita(idCita, conn);

        return new cita(idCita, nombreCliente, fecha, horario, horaFin, servicios, estado);
    }

    private List<servicios> cargarServiciosDeCita(int idCita, Connection conn) throws SQLException {
        String sql = """
            SELECT s.id_servicio, s.nombre, s.costo_unitario, s.categoria, s.activo
            FROM servicios s
            INNER JOIN cita_servicios cs ON s.id_servicio = cs.id_servicio
            WHERE cs.id_cita = ?
            ORDER BY cs.id_detalle ASC
            """;

        List<servicios> servicios = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCita);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    servicios.add(new servicios(
                        rs.getInt("id_servicio"),
                        rs.getString("nombre"),
                        new BigDecimal(rs.getString("costo_unitario")),
                        rs.getString("categoria"),
                        rs.getInt("activo") == 1
                    ));
                }
            }
        }
        return servicios;
    }

    private String escaparLike(String texto) {
        return texto.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }
}
