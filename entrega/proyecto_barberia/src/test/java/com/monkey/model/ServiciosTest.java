package com.monkey.model;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import org.junit.Test;

public class ServiciosTest {
    @Test
    public void constructorYCosto() {
        servicios s = new servicios("Corte", new BigDecimal("50.123"), "Cortes de pelo");
        assertEquals("Corte", s.getNombre());
        assertEquals(new BigDecimal("50.12"), s.getCostoUnitario());
        assertEquals("Cortes de pelo", s.getCategoria());
        assertTrue(s.isActivo());
        assertEquals(0, s.getIdServicio());
    }
    @Test
    public void constructorCompleto() {
        servicios s = new servicios(5, "Barba", new BigDecimal("20"), "Servicios adicionales", false);
        assertEquals(5, s.getIdServicio());
        assertFalse(s.isActivo());
    }
    @Test
    public void toStringFormato() {
        servicios s = new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo");
        assertEquals("Corte|$50.00|Cortes de pelo", s.toString());
    }
    @Test
    public void costoNull() {
        servicios s = new servicios("X", null, "Cat");
        assertEquals(new BigDecimal("0.00"), s.getCostoUnitario());
    }
}
