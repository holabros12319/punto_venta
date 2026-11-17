package com.monkey.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.logic.GestorAgenda;
import com.monkey.logic.GestorCatalogo;
import com.monkey.model.cita;
import com.monkey.model.servicios;

/**
 * Capa 1 - Pruebas de la pantalla de agenda.
 * Verifica la carga de la vista y las reglas de la tabla de solo lectura
 * (regla 15 del documento de arquitectura).
 */
public class VistaAgendaTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;
    private GestorAgenda gestorAgenda;
    private GestorCatalogo gestorCatalogo;
    private VistaAgenda vistaAgenda;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
        gestorAgenda = new GestorAgenda(repoCitas);
        gestorCatalogo = new GestorCatalogo(repoServicios);

        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
        repoServicios.insertar(new servicios("Barba", new BigDecimal("30.00"), "Otros servicios"));

        vistaAgenda = new VistaAgenda(gestorAgenda, gestorCatalogo);
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    private Object leerCampo(Object objetivo, String nombre) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        return campo.get(objetivo);
    }

    @Test
    public void refrescarServiciosCargaLosServiciosActivosDelCatalogo() throws Exception {
        vistaAgenda.refrescarServicios();

        javax.swing.DefaultListModel<?> modelo = (javax.swing.DefaultListModel<?>) leerCampo(vistaAgenda, "modeloServicios");
        assertEquals(2, modelo.getSize());
    }

    @Test
    public void losServiciosInactivosNoAparecenEnElSelectorDeLaAgenda() throws Exception {
        List<servicios> catalogo = repoServicios.listarTodos();
        catalogo.get(0).setActivo(false);
        repoServicios.actualizar(catalogo.get(0));

        vistaAgenda.refrescarServicios();

        javax.swing.DefaultListModel<?> modelo = (javax.swing.DefaultListModel<?>) leerCampo(vistaAgenda, "modeloServicios");
        assertEquals(1, modelo.getSize());
    }

    @Test
    public void refrescarHorariosCargaLosBloquesLibres() throws Exception {
        vistaAgenda.refrescarHorarios();

        javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) leerCampo(vistaAgenda, "comboHorarios");
        assertTrue("Debe haber horarios disponibles para una fecha futura.", combo.getItemCount() > 0);
    }

    @Test
    public void cargarTablaCitasMuestraLasCitasRegistradas() throws Exception {
        List<servicios> serviciosCita = new ArrayList<>(repoServicios.listarActivos());
        repoCitas.insertar(new cita("Ana Torres", LocalDate.now().plusDays(15), LocalTime.of(9, 0), serviciosCita));
        repoCitas.insertar(new cita("Luis Diaz", LocalDate.now().plusDays(16), LocalTime.of(10, 0), serviciosCita));

        vistaAgenda.cargarTablaCitas();

        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaAgenda, "modeloTabla");
        assertEquals(2, modelo.getRowCount());
        // El repositorio ordena por fecha descendente: la cita mas reciente aparece primero.
        assertEquals("Luis Diaz", modelo.getValueAt(0, 1));
        assertEquals("Ana Torres", modelo.getValueAt(1, 1));
        assertEquals("10:00", modelo.getValueAt(0, 3));
        assertEquals("11:00", modelo.getValueAt(0, 4));
        assertEquals("Pendiente", modelo.getValueAt(0, 7));
    }

    @Test
    public void laBusquedaPorClienteFiltraLaTabla() throws Exception {
        List<servicios> serviciosCita = new ArrayList<>(repoServicios.listarActivos());
        repoCitas.insertar(new cita("Ana Torres", LocalDate.now().plusDays(15), LocalTime.of(9, 0), serviciosCita));
        repoCitas.insertar(new cita("Luis Diaz", LocalDate.now().plusDays(16), LocalTime.of(10, 0), serviciosCita));

        javax.swing.JTextField txtBuscar = (javax.swing.JTextField) leerCampo(vistaAgenda, "txtBuscar");
        txtBuscar.setText("torres");
        vistaAgenda.cargarTablaCitas();

        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaAgenda, "modeloTabla");
        assertEquals(1, modelo.getRowCount());
    }

    @Test
    public void laTablaDeCitasEsDeSoloLectura() throws Exception {
        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaAgenda, "modeloTabla");
        assertFalse("Las celdas de la tabla no deben ser editables.", modelo.isCellEditable(0, 0));
        assertFalse(modelo.isCellEditable(0, 6));
    }
}
