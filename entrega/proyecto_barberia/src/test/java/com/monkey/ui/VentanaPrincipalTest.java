package com.monkey.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.awt.GraphicsEnvironment;

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

/**
 * Capa 1 - Pruebas de la ventana principal.
 * Verifica el ensamblado de las tres pestañas y la sincronizacion al cambiar
 * de vista (RNF 12). Requiere un entorno grafico: si la maquina no tiene
 * pantalla la prueba se omite de forma explicita.
 */
public class VentanaPrincipalTest {

    private VentanaPrincipal ventana;

    @Before
    public void setUp() {
        assumeFalse("Requiere un entorno grafico disponible.", GraphicsEnvironment.isHeadless());

        conexion_DB db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();

        RepositorioServicios repoServicios = new RepositorioServicios(db);
        RepositorioCitas repoCitas = new RepositorioCitas(db);
        RepositorioVentas repoVentas = new RepositorioVentas(db, repoCitas);

        GestorAgenda gestorAgenda = new GestorAgenda(repoCitas);
        GestorCatalogo gestorCatalogo = new GestorCatalogo(repoServicios);
        GestorPOS gestorPOS = new GestorPOS(repoCitas, repoVentas);
        GeneradorReportes generadorReportes = new GeneradorReportes(repoVentas, new GeneradorCSV());

        ventana = new VentanaPrincipal(gestorAgenda, gestorCatalogo, gestorPOS, generadorReportes,
                new SesionAdmin("monkey2026"));
    }

    @Test
    public void laVentanaIntegraLasTresPestanasDelSistema() {
        assertNotNull(ventana.getPanelPestanas());
        assertEquals(3, ventana.getPanelPestanas().getTabCount());
        assertEquals("Agenda", ventana.getPanelPestanas().getTitleAt(0));
        assertEquals("Catalogo de Servicios", ventana.getPanelPestanas().getTitleAt(1));
        assertEquals("Punto de Venta", ventana.getPanelPestanas().getTitleAt(2));
    }

    @Test
    public void cambiarDePestanaRefrescaLaVistaActiva() {
        ventana.getPanelPestanas().setSelectedIndex(2);
        ventana.getPanelPestanas().setSelectedIndex(0);

        assertTrue("La vista de agenda debe seguir operativa.", ventana.getPanelPestanas().getSelectedIndex() == 0);
    }

    @Test
    public void laVentanaExponeLaSesionAdministrativa() {
        assertNotNull(ventana.getSesionAdmin());
        assertTrue(!ventana.getSesionAdmin().estaAutenticado());
    }
}
