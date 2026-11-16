package com.monkey.data_persistance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.monkey.model.servicios;

public class RepositorioServicios {

    private final conexion_DB conexionSQL;

    public RepositorioServicios(conexion_DB conexionSQL) {
        this.conexionSQL = conexionSQL;
    }

    public void insertar(servicios servicio) {
        String sql = "INSERT INTO servicios (nombre, costo_unitario, categoria, activo) VALUES (?, ?, ?, ?)";

        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, servicio.getNombre().trim());
                ps.setString(2, servicio.getCostoUnitario().toPlainString());
                ps.setString(3, servicio.getCategoria());
                ps.setInt(4, servicio.isActivo() ? 1 : 0);

                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                throw new IllegalArgumentException("Ya existe un servicio registrado con ese nombre.", e);
            }
            throw new RuntimeException("Error al guardar el servicio en SQL: " + e.getMessage(), e);
        }
    }

    public void actualizar(servicios servicio) {
        String sql = "UPDATE servicios SET nombre = ?, costo_unitario = ?, categoria = ?, activo = ? WHERE id_servicio = ?";

        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, servicio.getNombre().trim());
                ps.setString(2, servicio.getCostoUnitario().toPlainString());
                ps.setString(3, servicio.getCategoria());
                ps.setInt(4, servicio.isActivo() ? 1 : 0);
                ps.setInt(5, servicio.getIdServicio());

                int filasAfectadas = ps.executeUpdate();
                if (filasAfectadas == 0) {
                    throw new IllegalArgumentException("No se encontró el servicio con ID: " + servicio.getIdServicio());
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                throw new IllegalArgumentException("Ya existe otro servicio con ese nombre.", e);
            }
            throw new RuntimeException("Error al actualizar el servicio en SQL: " + e.getMessage(), e);
        }
    }

    public List<servicios> listarTodos() {
        String sql = "SELECT id_servicio, nombre, costo_unitario, categoria, activo FROM servicios ORDER BY nombre ASC";
        return ejecutarConsultaLista(sql);
    }

    public List<servicios> listarActivos() {
        String sql = "SELECT id_servicio, nombre, costo_unitario, categoria, activo FROM servicios WHERE activo = 1 ORDER BY nombre ASC";
        return ejecutarConsultaLista(sql);
    }

    private List<servicios> ejecutarConsultaLista(String sql) {
        List<servicios> lista = new ArrayList<>();
        try {
            Connection conn = conexionSQL.obtenerConexion();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    lista.add(mapearFila(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar servicios en SQL: " + e.getMessage(), e);
        }
        return lista;
    }

    private servicios mapearFila(ResultSet rs) throws SQLException {
        int id = rs.getInt("id_servicio");
        String nombre = rs.getString("nombre");
        BigDecimal costo = new BigDecimal(rs.getString("costo_unitario"));
        String categoria = rs.getString("categoria");
        boolean activo = rs.getInt("activo") == 1;

        return new servicios(id, nombre, costo, categoria, activo);
    }
}