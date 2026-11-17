package com.monkey.data_persistance;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import com.monkey.model.cita;
import com.monkey.model.servicios;

public class RepositorioCitasTest {
    @Test
    public void insertarYObtener() {
        conexion_DB db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        RepositorioServicios rs = new RepositorioServicios(db);
        servicios s = new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo");
        rs.insertar(s);
        List<servicios> lista = rs.listarTodos();
        servicios guardado = lista.get(0);

        List<servicios> sv = new ArrayList<>();
        sv.add(guardado);
        cita c = new cita("Juan", LocalDate.of(2026,1,1), LocalTime.of(10,0), sv);
        RepositorioCitas rc = new RepositorioCitas(db);
        rc.insertar(c);
        assertTrue(c.getIdCita() > 0);

        cita obtenida = rc.obtenerPorId(c.getIdCita());
        assertNotNull(obtenida);
        assertEquals("Juan", obtenida.getNombreCliente());
        assertEquals(LocalTime.of(11, 0), obtenida.getHoraFin());
        assertEquals(1, obtenida.getServicios().size());

        List<LocalTime> ocupados = rc.obtenerHorariosOcupados(LocalDate.of(2026,1,1));
        assertEquals(1, ocupados.size());

        db.cerrarConexion();
    }
}
