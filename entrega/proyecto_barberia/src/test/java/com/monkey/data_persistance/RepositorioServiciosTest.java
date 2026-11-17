package com.monkey.data_persistance;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.Test;

import com.monkey.model.servicios;

public class RepositorioServiciosTest {
    @Test
    public void crudBasico() {
        conexion_DB db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        RepositorioServicios repo = new RepositorioServicios(db);
        servicios s = new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo");
        repo.insertar(s);
        List<servicios> lista = repo.listarTodos();
        assertFalse(lista.isEmpty());
        servicios guardado = lista.get(0);
        assertTrue(guardado.getIdServicio() > 0);
        guardado.setNombre("Corte Premium");
        repo.actualizar(guardado);
        List<servicios> activos = repo.listarActivos();
        assertEquals(1, activos.size());
        db.cerrarConexion();
    }
}
