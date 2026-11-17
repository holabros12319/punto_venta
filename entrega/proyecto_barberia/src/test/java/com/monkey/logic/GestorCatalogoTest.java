package com.monkey.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.model.servicios;

/**
 * Capa 2 - Pruebas del gestor de catalogo.
 * Verifica las categorias cerradas (RF 3), el alta (RF 4), la edicion (RF 5)
 * y la consulta aislada (RF 7).
 */
public class GestorCatalogoTest {

    private conexion_DB db;
    private RepositorioServicios repoServicios;
    private GestorCatalogo gestorCatalogo;

    @Before
    public void setUp() {
        db = new conexion_DB("jdbc:sqlite::memory:");
        db.inicializarTablas();
        repoServicios = new RepositorioServicios(db);
        gestorCatalogo = new GestorCatalogo(repoServicios);
    }

    @After
    public void tearDown() {
        db.cerrarConexion();
    }

    @Test
    public void lasCategoriasFijasSonCortesDePeloYOtrosServicios() {
        List<String> categorias = gestorCatalogo.obtenerCategorias();
        assertEquals(2, categorias.size());
        assertEquals("Cortes de pelo", categorias.get(0));
        assertEquals("Otros servicios", categorias.get(1));
    }

    @Test
    public void guardarServicioLoRegistraEnElCatalogo() {
        gestorCatalogo.guardarServicio(new servicios("Corte clasico", new BigDecimal("50.00"), "Cortes de pelo"));

        List<servicios> catalogo = gestorCatalogo.filtrarServicios("", true);
        assertEquals(1, catalogo.size());
        assertEquals("Corte clasico", catalogo.get(0).getNombre());
        assertTrue(catalogo.get(0).getIdServicio() > 0);
    }

    @Test
    public void guardarServicioRechazaNombresDuplicados() {
        gestorCatalogo.guardarServicio(new servicios("Corte clasico", new BigDecimal("50.00"), "Cortes de pelo"));

        try {
            gestorCatalogo.guardarServicio(new servicios("Corte clasico", new BigDecimal("60.00"), "Cortes de pelo"));
            fail("No se deben admitir servicios duplicados.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Ya existe"));
        }
        assertEquals(1, gestorCatalogo.filtrarServicios("", false).size());
    }

    @Test
    public void unServicioInactivoDebeReactivarseEnLugarDuplicarse() {
        gestorCatalogo.guardarServicio(new servicios("Barba", new BigDecimal("30.00"), "Otros servicios"));

        List<servicios> catalogo = gestorCatalogo.filtrarServicios("", false);
        servicios inactivo = catalogo.get(0);
        inactivo.setActivo(false);
        gestorCatalogo.editarServicio(inactivo);

        try {
            gestorCatalogo.guardarServicio(new servicios("Barba", new BigDecimal("35.00"), "Otros servicios"));
            fail("Un servicio inactivo no debe duplicarse, debe reactivarse.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("inactivo"));
        }

        assertEquals(1, gestorCatalogo.filtrarServicios("", false).size());
        assertTrue(gestorCatalogo.filtrarServicios("", true).isEmpty());
    }

    @Test
    public void editarServicioActualizaLosDatos() {
        gestorCatalogo.guardarServicio(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));

        List<servicios> catalogo = gestorCatalogo.filtrarServicios("", true);
        servicios servicio = catalogo.get(0);
        servicio.setNombre("Corte premium");
        servicio.setCostoUnitario(new BigDecimal("75.50"));
        gestorCatalogo.editarServicio(servicio);

        List<servicios> actualizado = gestorCatalogo.filtrarServicios("", true);
        assertEquals("Corte premium", actualizado.get(0).getNombre());
        assertEquals(new BigDecimal("75.50"), actualizado.get(0).getCostoUnitario());
    }

    @Test
    public void editarServicioRechazaUnRegistroInexistente() {
        servicios fantasma = new servicios(999, "Corte fantasma", new BigDecimal("10.00"), "Cortes de pelo", true);
        try {
            gestorCatalogo.editarServicio(fantasma);
            fail("No se puede editar un servicio inexistente.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No se encontro"));
        }
    }

    @Test
    public void filtrarServiciosBuscaPorCoincidenciaParcial() {
        gestorCatalogo.guardarServicio(new servicios("Corte clasico", new BigDecimal("50.00"), "Cortes de pelo"));
        gestorCatalogo.guardarServicio(new servicios("Corte premium", new BigDecimal("75.00"), "Cortes de pelo"));
        gestorCatalogo.guardarServicio(new servicios("Barba perfilada", new BigDecimal("30.00"), "Otros servicios"));

        assertEquals(3, gestorCatalogo.filtrarServicios("", true).size());
        assertEquals(2, gestorCatalogo.filtrarServicios("corte", true).size());
        assertEquals(1, gestorCatalogo.filtrarServicios("barba", true).size());
        assertEquals(0, gestorCatalogo.filtrarServicios("barba dePrecision", true).size());
        assertEquals(2, gestorCatalogo.filtrarServicios("corte", false).size());
    }

    @Test
    public void validarServicioExigeNombrePrecioYCategoriaValidos() {
        try {
            gestorCatalogo.guardarServicio(new servicios("   ", new BigDecimal("50.00"), "Cortes de pelo"));
            fail("El nombre no puede estar vacio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("nombre"));
        }

        try {
            gestorCatalogo.guardarServicio(new servicios("Corte", BigDecimal.ZERO.setScale(2), "Cortes de pelo"));
            fail("El precio debe ser mayor a cero.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mayor a cero"));
        }

        try {
            gestorCatalogo.guardarServicio(new servicios("Corte", new BigDecimal("50.00"), "Barberia premium"));
            fail("La categoria debe pertenecer a las categorias fijas.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("categoria"));
        }
    }

    @Test
    public void elGestorExigeUnRepositorioDeServicios() {
        try {
            new GestorCatalogo(null);
            fail("El repositorio de servicios es obligatorio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatorio"));
        }
    }

    @Test
    public void elFiltroPorEstadoOcultaLosServiciosInactivos() {
        gestorCatalogo.guardarServicio(new servicios("Corte", new BigDecimal("50.00"), "Cortes de pelo"));

        List<servicios> catalogo = gestorCatalogo.filtrarServicios("", false);
        catalogo.get(0).setActivo(false);
        gestorCatalogo.editarServicio(catalogo.get(0));

        assertTrue("El servicio inactivo no debe aparecer entre los activos.",
                gestorCatalogo.filtrarServicios("", true).isEmpty());
        assertEquals(1, gestorCatalogo.filtrarServicios("", false).size());
    }
}
