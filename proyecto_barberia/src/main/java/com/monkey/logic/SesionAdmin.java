package com.monkey.logic;

import java.time.Duration;
import java.time.LocalDateTime;

public class SesionAdmin {
    private static final long MAX_INACTIVITY_MINUTES = 10L;

    private boolean autenticado;
    private String pinConfigurado;
    private LocalDateTime ultimoAcceso;

    public SesionAdmin(String pinConfigurado) {
        if(pinConfigurado == null || pinConfigurado.isBlank()){
            throw new IllegalArgumentException("El PIN configurado no puede ser vacio");
        }
        this.pinConfigurado = pinConfigurado;
        this.autenticado = false;
        this.ultimoAcceso = null;
    }

    public boolean validarPin (String pinIngresado){
        if (pinIngresado == null || !pinConfigurado.equals(pinIngresado)){
            return false;
        }
        this.autenticado = true;
        this.ultimoAcceso = LocalDateTime.now();
        return true;
    }

    public boolean estaAutenticado() {
        if(!autenticado || ultimoAcceso == null){
            return false;
        }
        LocalDateTime ahora = LocalDateTime.now();
        long minutosInactivos = Duration.between(ultimoAcceso, ahora).toMinutes();
        if(minutosInactivos >= MAX_INACTIVITY_MINUTES){
            this.autenticado = false;
            return false;
        }
        this.ultimoAcceso = ahora;
        return true;
    }

    public void cerrarSesion(){
        this.autenticado = false;
        this.ultimoAcceso = null;
    }

}