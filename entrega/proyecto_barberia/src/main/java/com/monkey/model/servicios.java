package com.monkey.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
//definicion de la clase servicios
public class servicios {

    private int idServicio;
    private String nombre;
    private BigDecimal costoUnitario;
    private String categoria;
    private boolean activo;
    // primer constructor, sera usado posteiormente en la capa 3 del proyecto al reconstruir datos desde SQL
    public servicios(int idServicio, String nombre, BigDecimal costoUnitario, String categoria, boolean activo) {
        this.idServicio = idServicio;
        this.nombre = nombre;
        this.costoUnitario = (costoUnitario != null) ? costoUnitario.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale (2,RoundingMode.HALF_UP);
        this.categoria = categoria;
        this.activo = activo;
    }  
    //segundo constructor usado para  la creacion de un nuevo servicio, evitando llamar al constructor numero uno
    public servicios (String nombre, BigDecimal costoUnitario, String categoria) {
        this(0,nombre, costoUnitario, categoria, true);
    }
    
    public int getIdServicio() {
        return idServicio;
    }

    public void setIdServicio(int idServicio) {
        this.idServicio = idServicio;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public void setCostoUnitario(BigDecimal costoUnitario) {
        this.costoUnitario = (costoUnitario != null) ? costoUnitario.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return nombre + "|" + "$" +  costoUnitario.toPlainString() + "|" + categoria;
    }

}