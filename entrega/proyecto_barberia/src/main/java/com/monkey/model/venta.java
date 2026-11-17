package com.monkey.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class venta {

    private final int idVenta;
    private final cita cita;
    private final String snapshotServicios;
    private final BigDecimal total;
    private final BigDecimal efectivo;
    private final BigDecimal cambio;
    private final LocalDateTime fechaHora;

    public venta(int idVenta, cita cita, String snapshotServicios, BigDecimal total, BigDecimal efectivo, BigDecimal cambio, LocalDateTime fechaHora) {
        this.idVenta = idVenta;
        this.cita = cita;
        this.snapshotServicios = snapshotServicios;
        this.total = total.setScale(2, RoundingMode.HALF_UP);
        this.efectivo = efectivo.setScale(2, RoundingMode.HALF_UP);
        this.cambio = cambio.setScale(2, RoundingMode.HALF_UP);
        this.fechaHora = fechaHora;
    }

    public venta(cita cita, String snapshotServicios, BigDecimal total, BigDecimal efectivo, BigDecimal cambio) {
        this(0, cita, snapshotServicios, total, efectivo, cambio, LocalDateTime.now());
    }

    public int getIdVenta() {
        return idVenta;
    }

    public cita getCita() {
        return cita;
    }

    public String getSnapshotServicios() {
        return snapshotServicios;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getEfectivo() {
        return efectivo;
    }

    public BigDecimal getCambio() {
        return cambio;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
    //metodo para dar formato al ticket
    public String formatoTicket (){

        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String cliente = (cita != null) ? cita.getNombreCliente() : "Sin cliente";
        return "Ticket #" + idVenta
                + " | Cliente: " + cliente
                + " | Servicios: " + snapshotServicios
                + " | Total: $" + total.toPlainString()
                + " | Fecha: " + fechaHora.format(formato);
    }
    
}
