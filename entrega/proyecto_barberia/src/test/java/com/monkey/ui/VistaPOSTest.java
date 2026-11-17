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

import com.monkey.data_persistance.GeneradorCSV;
import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.logic.GeneradorReportes;
import com.monkey.logic.GestorAgenda;
import com.monkey.logic.GestorCatalogo;
import com.monkey.logic.GestorPOS;
import com.monkey.logic.SesionAdmin;
import com.monkey.model.cita;
import com.monkey.model.servicios;

/**
 * Capa 1 - Pruebas de la pantalla de punto de venta.
 * Verifica la importacion de citas cobrables (RF 14), el calculo del cambio
 * en vivo (RF 15) y la tabla de solo lectura (regla 15).
 */
public class VistaPOSTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private RepositorioCitas repoCitas;
    private VentanaPrincipal ventanaPrincipal;
    private VistaPOS vistaPOS;

    @Before
    public void setUp() {
        org.junit.Assume.assumeFalse("Requiere un entorno grafico disponible.",
                java.awt.GraphicsEnvironment.isHeadless());

        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        repoCitas = new RepositorioCitas(db);
        RepositorioVentas repoVentas = new RepositorioVentas(db, repoCitas);

        repoServicios.insertar(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));
        repoServicios.insertar(new servicios("Barba", new BigDecimal("30.00"), "Otros servicios"));

        GestorAgenda gestorAgenda = new GestorAgenda(repoCitas);
        GestorCatalogo gestorCatalogo = new GestorCatalogo(repoServicios);
        GestorPOS gestorPOS = new GestorPOS(repoCitas, repoVentas);
        GeneradorReportes generadorReportes = new GeneradorReportes(repoVentas, new GeneradorCSV());

        ventanaPrincipal = new VentanaPrincipal(gestorAgenda, gestorCatalogo, gestorPOS, generadorReportes,
                new SesionAdmin("monkey2026"));
        vistaPOS = new VistaPOS(gestorPOS, generadorReportes, ventanaPrincipal);
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.cerrarConexion();
        }
    }

    private Object leerCampo(Object objetivo, String nombre) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        return campo.get(objetivo);
    }

    private void escribirCampo(Object objetivo, String nombre, Object valor) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }

    private cita registrarCita(String cliente, LocalTime hora) {
        List<servicios> serviciosCita = new ArrayList<>(repoServicios.listarActivos());
        cita nueva = new cita(cliente, LocalDate.now().plusDays(10), hora, serviciosCita);
        repoCitas.insertar(nueva);
        return nueva;
    }

    @Test
    public void buscarCitasActivasMuestraSoloLasCobrables() throws Exception {
        registrarCita("Ana Torres", LocalTime.of(9, 0));
        registrarCita("Luis Diaz", LocalTime.of(10, 0));
        cita cancelada = registrarCita("Carlos Paz", LocalTime.of(11, 0));
        repoCitas.actualizarEstado(cancelada.getIdCita(), "Cancelada");

        vistaPOS.buscarCitasActivas();

        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaPOS, "modeloTabla");
        assertEquals(2, modelo.getRowCount());
        assertEquals("$80.00", modelo.getValueAt(0, 5));
    }

    @Test
    public void laTablaDePendientesEsDeSoloLectura() throws Exception {
        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaPOS, "modeloTabla");
        assertFalse(modelo.isCellEditable(0, 0));
    }

    @Test
    public void elCambioSeCalculaEnVivoAlEscribirElEfectivo() throws Exception {
        cita pendiente = registrarCita("Ana Torres", LocalTime.of(9, 0));
        escribirCampo(vistaPOS, "citaActiva", pendiente);

        javax.swing.JTextField txtEfectivo = (javax.swing.JTextField) leerCampo(vistaPOS, "txtEfectivo");
        javax.swing.JLabel lblCambio = (javax.swing.JLabel) leerCampo(vistaPOS, "lblCambio");

        txtEfectivo.setText("100");
        assertEquals("$20.00", lblCambio.getText());

        txtEfectivo.setText("80");
        assertEquals("$0.00", lblCambio.getText());
    }

    @Test
    public void unEfectivoIncompletoMuestraCeroSinInterrumpirAlUsuario() throws Exception {
        cita pendiente = registrarCita("Ana Torres", LocalTime.of(9, 0));
        escribirCampo(vistaPOS, "citaActiva", pendiente);

        javax.swing.JTextField txtEfectivo = (javax.swing.JTextField) leerCampo(vistaPOS, "txtEfectivo");
        javax.swing.JLabel lblCambio = (javax.swing.JLabel) leerCampo(vistaPOS, "lblCambio");

        txtEfectivo.setText("");
        assertEquals("$0.00", lblCambio.getText());

        txtEfectivo.setText("12a");
        assertEquals("$0.00", lblCambio.getText());

        txtEfectivo.setText("50");
        assertEquals("$0.00", lblCambio.getText());
    }

    @Test
    public void sinCitaSeleccionadaElCambioPermaneceEnCero() throws Exception {
        javax.swing.JTextField txtEfectivo = (javax.swing.JTextField) leerCampo(vistaPOS, "txtEfectivo");
        javax.swing.JLabel lblCambio = (javax.swing.JLabel) leerCampo(vistaPOS, "lblCambio");

        txtEfectivo.setText("100");
        assertEquals("$0.00", lblCambio.getText());
    }

    @Test
    public void laVistaExigeSusDependencias() {
        try {
            new VistaPOS(null, null, ventanaPrincipal);
            org.junit.Assert.fail("El gestor de punto de venta es obligatorio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatorios"));
        }
    }
}
