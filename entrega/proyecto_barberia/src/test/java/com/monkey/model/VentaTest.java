package com.monkey.model;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.Test;

public class VentaTest {
    @Test
    public void formatoTicket() {
        cita c = new cita("Ana", java.time.LocalDate.of(2026,2,2), java.time.LocalTime.of(11,0), null);
        venta v = new venta(1, c, "Corte", new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("50"), LocalDateTime.of(2026,2,2,11,0));
        String t = v.formatoTicket();
        assertTrue(t.contains("Ticket #1"));
        assertTrue(t.contains("Ana"));
        assertTrue(t.contains("Corte"));
        assertEquals(new BigDecimal("50.00"), v.getTotal());
        assertEquals(new BigDecimal("100.00"), v.getEfectivo());
        assertEquals(new BigDecimal("50.00"), v.getCambio());
    }
    @Test
    public void constructorSimplificado() {
        cita c = new cita("Bob", java.time.LocalDate.of(2026,3,3), java.time.LocalTime.of(9,0), null);
        venta v = new venta(c, "Barba", new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("0"));
        assertEquals(0, v.getIdVenta());
        assertNotNull(v.getFechaHora());
    }
}
