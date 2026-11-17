package com.monkey.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.logic.GeneradorReportes;
import com.monkey.logic.GestorAgenda;
import com.monkey.logic.GestorCatalogo;
import com.monkey.logic.GestorPOS;
import com.monkey.data_persistance.GeneradorCSV;
import com.monkey.model.servicios;

/**
 * Capa 1 - Pruebas de la pantalla de catalogo.
 * Verifica la carga de la tabla, el filtro por nombre y el estado, y que la
 * tabla sea de solo lectura (regla 15 del documento de arquitectura).
 */
public class VistaCatalogoTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private GestorCatalogo gestorCatalogo;
    private VentanaPrincipal ventanaPrincipal;
    private VistaCatalogo vistaCatalogo;

    @Before
    public void setUp() {
        org.junit.Assume.assumeFalse("Requiere un entorno grafico disponible.",
                java.awt.GraphicsEnvironment.isHeadless());

        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        gestorCatalogo = new GestorCatalogo(repoServicios);

        repoServicios.insertar(new servicios("Corte clasico", new BigDecimal("50.00"), "Cortes de pelo"));
        repoServicios.insertar(new servicios("Barba", new BigDecimal("30.00"), "Otros servicios"));

        RepositorioCitas repoCitas = new RepositorioCitas(db);
        RepositorioVentas repoVentas = new RepositorioVentas(db, repoCitas);
        GestorAgenda gestorAgenda = new GestorAgenda(repoCitas);
        GestorPOS gestorPOS = new GestorPOS(repoCitas, repoVentas);
        GeneradorReportes generadorReportes = new GeneradorReportes(repoVentas, new GeneradorCSV());

        ventanaPrincipal = new VentanaPrincipal(gestorAgenda, gestorCatalogo, gestorPOS, generadorReportes,
                new com.monkey.logic.SesionAdmin("monkey2026"));
        vistaCatalogo = new VistaCatalogo(gestorCatalogo, ventanaPrincipal);
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

    @Test
    public void cargarTablaServiciosMuestraElCatalogo() throws Exception {
        vistaCatalogo.cargarTablaServicios();

        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaCatalogo, "modeloTabla");
        assertEquals(2, modelo.getRowCount());
        assertEquals("Barba", modelo.getValueAt(0, 1));
        assertEquals("Otros servicios", modelo.getValueAt(0, 3));
    }

    @Test
    public void elFiltroPorNombreAislaLaConsultaDelCatalogo() throws Exception {
        javax.swing.JTextField txtFiltro = (javax.swing.JTextField) leerCampo(vistaCatalogo, "txtFiltro");
        txtFiltro.setText("barba");

        vistaCatalogo.cargarTablaServicios();

        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaCatalogo, "modeloTabla");
        assertEquals(1, modelo.getRowCount());
        assertEquals("Barba", modelo.getValueAt(0, 1));
    }

    @Test
    public void losServiciosInactivosSoloAparecenSiSeSolicitan() throws Exception {
        List<servicios> catalogo = repoServicios.listarTodos();
        catalogo.get(0).setActivo(false);
        repoServicios.actualizar(catalogo.get(0));

        vistaCatalogo.cargarTablaServicios();
        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaCatalogo, "modeloTabla");
        assertEquals("Por defecto solo se listan los servicios activos.", 1, modelo.getRowCount());

        javax.swing.JCheckBox chkInactivos = (javax.swing.JCheckBox) leerCampo(vistaCatalogo, "chkIncluirInactivos");
        chkInactivos.setSelected(true);
        vistaCatalogo.cargarTablaServicios();
        assertEquals(2, modelo.getRowCount());
    }

    @Test
    public void laTablaDeServiciosEsDeSoloLectura() throws Exception {
        javax.swing.table.DefaultTableModel modelo = (javax.swing.table.DefaultTableModel) leerCampo(vistaCatalogo, "modeloTabla");
        assertFalse(modelo.isCellEditable(0, 0));
    }

    @Test
    public void elComboCategoriaCargaLasCategoriasFijas() throws Exception {
        javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) leerCampo(vistaCatalogo, "comboCategoria");
        assertEquals(2, combo.getItemCount());
        assertEquals("Cortes de pelo", combo.getItemAt(0));
        assertEquals("Otros servicios", combo.getItemAt(1));
    }

    @Test
    public void exponeUnaAccionParaDefinirOCambiarLaContrasena() throws Exception {
        javax.swing.JButton boton = (javax.swing.JButton) leerCampo(vistaCatalogo, "btnCambiarPin");
        assertEquals("Definir / Cambiar contraseña", boton.getText());
        assertTrue(boton.isVisible());
        assertTrue("El botón debe formar parte de la pantalla del catálogo.", boton.getParent() != null);
    }

    @Test
    public void laVistaExigeElGestorYLaVentanaPadre() {
        try {
            new VistaCatalogo(null, ventanaPrincipal);
            fail("El gestor de catalogo es obligatorio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatorio"));
        }
        try {
            new VistaCatalogo(gestorCatalogo, null);
            fail("La ventana principal es obligatoria.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatoria"));
        }
    }
}
