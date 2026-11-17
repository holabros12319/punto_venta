package com.monkey.data_persistance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.monkey.model.cita;
import com.monkey.model.venta;

/**
 * Capa 3 - Persistencia de datos.
 *
 * Registra las ventas en SQLite dentro de una unica transaccion (RNF 11):
 * se inserta el ticket y se actualiza la cita a "Completada" en el mismo
 * commit, de modo que nunca exista una venta sin su cita liquidada ni una
 * cita cobrada sin su venta (invariante de unicidad de liquidacion).
 *
 * Las tablas y los triggers de inmutabilidad (RNF 12) son creados por
 * {@link conexion_DB#inicializarTablas()}; esta clase solo ejecuta DML.
 */
public class RepositorioVentas {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final conexion_DB conexionSQL;
    private final RepositorioCitas repoCitas;

    public RepositorioVentas(conexion_DB conexionSQL, RepositorioCitas repoCitas) {
        if (conexionSQL == null) {
            throw new IllegalArgumentException("La conexion a la base de datos es obligatoria.");
        }
        if (repoCitas == null) {
            throw new IllegalArgumentException("El repositorio de citas es obligatorio.");
        }
        this.conexionSQL = conexionSQL;
        this.repoCitas = repoCitas;
    }

    /**
     * Registra la venta y marca la cita como "Completada" de forma atomica.
     * Ante cualquier error se revierte la transaccion completa (ROLLBACK).
     */
    public void registrarVentaTransaccional(venta ventaARegistrar) {
        if (ventaARegistrar == null) {
            throw new IllegalArgumentException("La venta a registrar no puede ser nula.");
        }
        if (ventaARegistrar.getCita() == null || ventaARegistrar.getCita().getIdCita() <= 0) {
            throw new IllegalArgumentException("La venta debe estar asociada a una cita registrada.");
        }
        if (ventaARegistrar.getTotal() == null
                || ventaARegistrar.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total de la venta debe ser mayor a cero.");
        }
        if (ventaARegistrar.getEfectivo() == null
                || ventaARegistrar.getEfectivo().compareTo(ventaARegistrar.getTotal()) < 0) {
            throw new IllegalArgumentException("El efectivo recibido no cubre el total de la venta.");
        }
        if (ventaARegistrar.getCambio() == null
                || ventaARegistrar.getCambio().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El cambio de la venta no puede ser negativo.");
        }

        String sql = "INSERT INTO ventas (id_cita, snapshot_servicios, total, efectivo, cambio, fecha_hora) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = conexionSQL.obtenerConexion();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, ventaARegistrar.getCita().getIdCita());
                ps.setString(2, ventaARegistrar.getSnapshotServicios());
                ps.setString(3, ventaARegistrar.getTotal().toPlainString());
                ps.setString(4, ventaARegistrar.getEfectivo().toPlainString());
                ps.setString(5, ventaARegistrar.getCambio().toPlainString());
                ps.setString(6, ventaARegistrar.getFechaHora().format(FORMATO_FECHA_HORA));
                ps.executeUpdate();
            }

            // La cita solo puede pasar a "Completada" si el trigger de la base lo permite.
            repoCitas.actualizarEstado(ventaARegistrar.getCita().getIdCita(), "Completada", conn);

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error al revertir la transaccion de la venta: " + ex.getMessage());
                }
            }
            String mensaje = (e.getMessage() != null) ? e.getMessage() : "";
            if (mensaje.contains("UNIQUE constraint failed")) {
                throw new IllegalStateException("La cita ya fue cobrada anteriormente. No se permite generar dos ventas para la misma cita.", e);
            }
            if (mensaje.contains("Solo se pueden cobrar citas")) {
                throw new IllegalStateException("La cita ya fue cobrada anteriormente o su estado actual no es cobrable (Pendiente o Confirmada).", e);
            }
            throw new RuntimeException("Error al registrar la venta en SQL: " + e.getMessage(), e);
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

    /**
     * Recupera la venta ya registrada en la base de datos para una cita.
     * A diferencia del objeto en memoria, aqui el idVenta si es el real.
     */
    public venta obtenerPorCita(int idCita) {
        String sql = "SELECT id_venta, id_cita, snapshot_servicios, total, efectivo, cambio, fecha_hora "
                + "FROM ventas WHERE id_cita = ?";

        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idCita);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return construirVentaDesdeFila(rs);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar la venta por cita: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Lista todas las ventas registradas ordenadas por fecha y hora.
     */
    public List<venta> listarTodas() {
        String sql = "SELECT id_venta, id_cita, snapshot_servicios, total, efectivo, cambio, fecha_hora "
                + "FROM ventas ORDER BY fecha_hora ASC, id_venta ASC";
        return ejecutarConsulta(sql, null, null);
    }

    /**
     * Lista las ventas registradas dentro de un rango de fechas (inclusive).
     * Si las fechas son nulas se devuelve el historico completo.
     */
    public List<venta> listarPorRangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return listarTodas();
        }
        String sql = "SELECT id_venta, id_cita, snapshot_servicios, total, efectivo, cambio, fecha_hora "
                + "FROM ventas WHERE fecha_hora >= ? AND fecha_hora < ? ORDER BY fecha_hora ASC, id_venta ASC";

        LocalDate fechaInicio = (desde != null) ? desde : LocalDate.of(1970, 1, 1);
        LocalDate fechaFin = (hasta != null) ? hasta.plusDays(1) : LocalDate.of(2999, 12, 31);

        return ejecutarConsulta(sql, fechaInicio.toString(), fechaFin.toString());
    }

    /**
     * Lista las ventas registradas dentro de un periodo mensual (YearMonth).
     * Si el periodo es nulo se devuelve el historico completo.
     */
    public List<venta> listarPorPeriodo(YearMonth periodo) {
        if (periodo == null) {
            return listarTodas();
        }
        String sql = "SELECT id_venta, id_cita, snapshot_servicios, total, efectivo, cambio, fecha_hora "
                + "FROM ventas WHERE fecha_hora >= ? AND fecha_hora < ? ORDER BY fecha_hora ASC, id_venta ASC";

        String inicio = periodo.atDay(1).toString();
        String fin = periodo.plusMonths(1).atDay(1).toString();

        return ejecutarConsulta(sql, inicio, fin);
    }

    private List<venta> ejecutarConsulta(String sql, String parametroInicio, String parametroFin) {
        List<venta> ventas = new ArrayList<>();
        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int indice = 1;
                if (parametroInicio != null) {
                    ps.setString(indice++, parametroInicio);
                    ps.setString(indice, parametroFin);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ventas.add(construirVentaDesdeFila(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar las ventas en SQL: " + e.getMessage(), e);
        }
        return ventas;
    }

    private venta construirVentaDesdeFila(ResultSet rs) throws SQLException {
        int idVenta = rs.getInt("id_venta");
        int idCita = rs.getInt("id_cita");
        String snapshot = rs.getString("snapshot_servicios");
        BigDecimal total = new BigDecimal(rs.getString("total"));
        BigDecimal efectivo = new BigDecimal(rs.getString("efectivo"));
        BigDecimal cambio = new BigDecimal(rs.getString("cambio"));
        LocalDateTime fechaHora = LocalDateTime.parse(rs.getString("fecha_hora"), FORMATO_FECHA_HORA);

        cita citaAsociada = repoCitas.obtenerPorId(idCita);

        return new venta(idVenta, citaAsociada, snapshot, total, efectivo, cambio, fechaHora);
    }
}
