package com.monkey.model;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CitaTest {
    @Test
    public void constructorYGetters() {
        List<servicios> sv = new ArrayList<>();
        sv.add(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
        cita c = new cita("Juan", LocalDate.of(2026,1,1), LocalTime.of(10,0), sv);
        assertEquals("Juan", c.getNombreCliente());
        assertEquals(LocalDate.of(2026,1,1), c.getFecha());
        assertEquals(LocalTime.of(10,0), c.getHora());
        assertEquals(LocalTime.of(11,0), c.getHoraFin());
        assertEquals(1, c.getServicios().size());
        assertEquals("Pendiente", c.getEstado());
    }

    @Test
    public void horaFinSeDerivaDelBloqueDeUnaHora() {
        cita c = new cita("Juan", LocalDate.of(2026, 1, 1), LocalTime.of(19, 0), null);
        assertEquals(LocalTime.of(20, 0), c.getHoraFin());
    }
    @Test
    public void calcularCostoTotal() {
        List<servicios> sv = new ArrayList<>();
        sv.add(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
        sv.add(new servicios("Barba", new BigDecimal("20.00"), "Servicios adicionales"));
        cita c = new cita("Juan", LocalDate.of(2026,1,1), LocalTime.of(10,0), sv);
        assertEquals(new BigDecimal("70.00"), c.calcularCostoTotal());
    }
    @Test
    public void setServiciosDefensivo() {
        cita c = new cita("Juan", LocalDate.of(2026,1,1), LocalTime.of(10,0), null);
        assertTrue(c.getServicios().isEmpty());
        c.setServicios(null);
        assertTrue(c.getServicios().isEmpty());
    }
}
