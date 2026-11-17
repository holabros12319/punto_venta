package com.monkey.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.model.cita;
import com.monkey.model.servicios;
import com.monkey.model.venta;

/**
 * Capa 2 - Logica de negocio del punto de venta.
 *
 * Importa las citas cobrables (RF 14), valida el efectivo y calcula el cambio
 * (RF 15) y registra la venta de forma transaccional revalidando el estado
 * de la cita contra la base de datos (RF 16, RNF 11).
 */
public class GestorPOS {

    private static final String ESTADO_PENDIENTE = "Pendiente";
    private static final String ESTADO_CONFIRMADA = "Confirmada";

    private final RepositorioCitas repoCitas;
    private final RepositorioVentas repoVentas;

    public GestorPOS(RepositorioCitas repoCitas, RepositorioVentas repoVentas) {
        if (repoCitas == null) {
            throw new IllegalArgumentException("El repositorio de citas es obligatorio.");
        }
        if (repoVentas == null) {
            throw new IllegalArgumentException("El repositorio de ventas es obligatorio.");
        }
        this.repoCitas = repoCitas;
        this.repoVentas = repoVentas;
    }

    /**
     * Citas en estado Pendiente o Confirmada que todavia no fueron cobradas (RF 14).
     * El filtro por cliente es una coincidencia parcial reutilizable (RF 13).
     */
    public List<cita> buscarCitasPorCobrar(String cliente) {
        return repoCitas.listarCobrables(cliente);
    }

    /**
     * Cambio a devolver: nunca negativo y siempre con escala de dos decimales.
     */
    public BigDecimal calcularCambio(BigDecimal total, BigDecimal efectivo) {
        if (total == null || efectivo == null) {
            throw new IllegalArgumentException("El total y el efectivo son obligatorios para calcular el cambio.");
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total a cobrar debe ser mayor a cero.");
        }
        if (efectivo.compareTo(total) < 0) {
            throw new IllegalArgumentException("El efectivo recibido no cubre el total a cobrar.");
        }
        return efectivo.subtract(total).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Procesa el cobro de una cita.
     *
     * Revalida el estado de la cita consultando la base de datos (no se confía
     * en el objeto en memoria), construye el respaldo historico de los servicios
     * y registra la venta junto con el cambio de estado a "Completada".
     */
    public venta procesarCobro(int idCita, BigDecimal efectivo) {
        if (efectivo == null) {
            throw new IllegalArgumentException("Debe indicar el efectivo recibido.");
        }
        if (efectivo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El efectivo recibido no puede ser negativo.");
        }

        // Revalidacion fresca contra SQL (RNF 11 / regla de integridad financiera).
        cita citaActual = repoCitas.obtenerPorId(idCita);
        if (citaActual == null) {
            throw new IllegalArgumentException("No se encontro la cita con ID: " + idCita);
        }

        String estado = citaActual.getEstado();
        if (!ESTADO_PENDIENTE.equals(estado) && !ESTADO_CONFIRMADA.equals(estado)) {
            throw new IllegalStateException("La cita no es cobrable porque su estado actual es \"" + estado + "\".");
        }

        List<servicios> servicios = citaActual.getServicios();
        if (servicios.isEmpty()) {
            throw new IllegalStateException("La cita no tiene servicios asociados, no se puede cobrar.");
        }

        BigDecimal total = citaActual.calcularCostoTotal();
        BigDecimal cambio = calcularCambio(total, efectivo);
        String snapshot = construirSnapshotServicios(servicios);

        venta nuevaVenta = new venta(citaActual, snapshot, total, efectivo, cambio);
        repoVentas.registrarVentaTransaccional(nuevaVenta);
        return nuevaVenta;
    }

    /**
     * Recupera la venta ya persistida de una cita. Permite conocer el
     * identificador real de la venta para el comprobante en pantalla (RF 17).
     */
    public venta obtenerVentaRegistrada(int idCita) {
        return repoVentas.obtenerPorCita(idCita);
    }

    /**
     * Respaldo historico de los servicios cobrados: nombre y precio vigente al
     * momento de la venta. Este texto es el que queda almacenado en la venta.
     */
    private String construirSnapshotServicios(List<servicios> servicios) {
        StringBuilder snapshot = new StringBuilder();
        for (servicios servicio : servicios) {
            if (servicio == null) {
                continue;
            }
            if (snapshot.length() > 0) {
                snapshot.append("; ");
            }
            snapshot.append(servicio.getNombre().trim());
            snapshot.append(" ($");
            snapshot.append(servicio.getCostoUnitario().toPlainString());
            snapshot.append(")");
        }
        return snapshot.toString();
    }
}
