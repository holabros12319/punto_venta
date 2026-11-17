package com.monkey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.monkey.data_persistance.RepositorioPin;
import com.monkey.logic.SesionAdmin;

/**
 * Pruebas del arranque de la aplicacion.
 * Verifica la autonomia operativa: carpeta local del usuario, base de datos
 * SQLite embebida y credencial administrativa preconfigurada (RF 1, RNF 16).
 */
public class AppTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    @Test
    public void laCarpetaDeDatosViveEnElDirectorioDelUsuario() {
        String ruta = App.resolverCarpetaDatos();
        assertTrue(ruta.contains(App.NOMBRE_CARPETA));
        assertTrue(ruta.contains(System.getProperty("user.home")));
    }

    @Test
    public void laUrlSqliteApuntaAlArchivoLocal() {
        String url = App.construirUrlSqlite("/tmp/monkey");
        assertTrue(url.startsWith("jdbc:sqlite:"));
        assertTrue(url.endsWith(App.NOMBRE_ARCHIVO_DB));
    }

    @Test
    public void crearCarpetaGeneraElDirectorioLocal() throws Exception {
        String destino = new File(carpeta.getRoot(), "sub/carpeta").getAbsolutePath();
        App.crearCarpeta(destino);
        assertTrue(new File(destino).isDirectory());
    }

    @Test
    public void elPinInicialSeAlmacenaComoHashEnLaPrimeraEjecucion() throws Exception {
        String destino = new File(carpeta.getRoot(), "datos").getAbsolutePath();
        App.crearCarpeta(destino);

        RepositorioPin repositorio = new RepositorioPin(new File(destino));
        SesionAdmin sesion = new SesionAdmin(App.PIN_INICIAL, repositorio);
        String valorGuardado = repositorio.leerConfiguracion();

        assertTrue(valorGuardado.startsWith("pbkdf2-sha256$"));
        assertFalse(valorGuardado.contains(App.PIN_INICIAL));
        assertTrue(sesion.validarPin(App.PIN_INICIAL));
    }

    @Test
    public void elCambioDePinPersisteTrasReiniciarLaAplicacion() throws Exception {
        String destino = new File(carpeta.getRoot(), "datos").getAbsolutePath();
        App.crearCarpeta(destino);
        RepositorioPin repositorio = new RepositorioPin(new File(destino));
        SesionAdmin primeraSesion = new SesionAdmin(App.PIN_INICIAL, repositorio);
        primeraSesion.cambiarPin(App.PIN_INICIAL, "nuevo123");

        SesionAdmin segundaSesion = new SesionAdmin(App.PIN_INICIAL, repositorio);
        assertFalse(segundaSesion.validarPin(App.PIN_INICIAL));
        assertTrue(segundaSesion.validarPin("nuevo123"));
    }
}
