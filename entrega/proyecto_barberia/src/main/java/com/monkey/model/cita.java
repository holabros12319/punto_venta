package com.monkey.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class cita {

    private int idCita;
    private String nombreCliente;
    private LocalDate fecha;
    private LocalTime hora;
    private LocalTime horaFin;
    private List<servicios> servicios;
    private String estado;

    public cita(int idCita, String nombreCliente, LocalDate fecha, LocalTime hora, List<servicios> servicios, String estado) {
        this(idCita, nombreCliente, fecha, hora, calcularHoraFin(hora), servicios, estado);
    }

    public cita(int idCita, String nombreCliente, LocalDate fecha, LocalTime hora, LocalTime horaFin,
            List<servicios> servicios, String estado) {
        this.idCita = idCita;
        this.nombreCliente = nombreCliente;
        this.fecha = fecha;
        this.hora = hora;
        this.horaFin = horaFin;
        this.servicios = (servicios !=null) ? new ArrayList<>(servicios) : new ArrayList<>();
        this.estado = estado;
    }

    public cita (String nombreCliente, LocalDate fecha, LocalTime hora, List<servicios> servicios) {
        this(0, nombreCliente, fecha, hora, servicios, "Pendiente");
    }

    public int getIdCita() {
        return idCita;
    }

    public void setIdCita(int idCita) {
        this.idCita = idCita;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
        this.horaFin = calcularHoraFin(hora);
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        if (this.hora != null && horaFin != null && !calcularHoraFin(this.hora).equals(horaFin)) {
            throw new IllegalArgumentException("La cita debe durar un bloque de una hora.");
        }
        this.horaFin = horaFin;
    }

    public List<servicios> getServicios() {
        return new ArrayList<>(servicios);
    }

    public void setServicios(List<servicios> servicios) {
        this.servicios = (servicios != null) ? new ArrayList<>(servicios) : new ArrayList<>();
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
    // metodo para sumar el total de todos los servicios de una cita
    public BigDecimal calcularCostoTotal(){
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for(servicios s : servicios){
            if (s != null && s.getCostoUnitario() != null) {
                total = total.add(s.getCostoUnitario());
            }
        }
        return total;
    }

    private static LocalTime calcularHoraFin(LocalTime hora) {
        return hora == null ? null : hora.plusHours(1);
    }
    
}
