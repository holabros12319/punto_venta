package com.monkey.logic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.monkey.data_persistance.RepositorioPin;

/**
 * Capa 2 - Pruebas de la sesion administrativa.
 * Verifica la validacion del PIN (RF 1) y la expiracion pasiva de la sesion
 * tras 10 minutos sin actividad.
 */
public class SesionAdminTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    @Test
    public void elPinCorrectoAutenticaLaSesion() {
        SesionAdmin sesion = new SesionAdmin("monkey2026");

        assertFalse(sesion.estaAutenticado());
        assertTrue(sesion.validarPin("monkey2026"));
        assertTrue(sesion.estaAutenticado());
    }

    @Test
    public void unPinIncorrectoNoAutenticaLaSesion() {
        SesionAdmin sesion = new SesionAdmin("monkey2026");

        assertFalse(sesion.validarPin("123456"));
        assertFalse(sesion.estaAutenticado());
        assertFalse(sesion.validarPin((String) null));
    }

    @Test
    public void cerrarSesionInvalidaElAcceso() {
        SesionAdmin sesion = new SesionAdmin("monkey2026");
        sesion.validarPin("monkey2026");
        assertTrue(sesion.estaAutenticado());

        sesion.cerrarSesion();
        assertFalse(sesion.estaAutenticado());
    }

    @Test
    public void laSesionExpiraTrasDiezMinutosSinActividad() throws Exception {
        SesionAdmin sesion = new SesionAdmin("monkey2026");
        sesion.validarPin("monkey2026");

        Field campo = SesionAdmin.class.getDeclaredField("ultimoAcceso");
        campo.setAccessible(true);
        campo.set(sesion, LocalDateTime.now().minusMinutes(11));

        assertFalse("La sesion debe cerrarse tras 10 minutos sin actividad.", sesion.estaAutenticado());
    }

    @Test
    public void laSesionSeRenuevaConActividad() throws Exception {
        SesionAdmin sesion = new SesionAdmin("monkey2026");
        sesion.validarPin("monkey2026");

        Field campo = SesionAdmin.class.getDeclaredField("ultimoAcceso");
        campo.setAccessible(true);
        campo.set(sesion, LocalDateTime.now().minusMinutes(9));

        assertTrue(sesion.estaAutenticado());
        assertTrue(sesion.estaAutenticado());
    }

    @Test
    public void laSesionExigeUnPinConfigurado() {
        try {
            new SesionAdmin("   ");
            fail("El PIN configurado no puede estar vacio.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("6 y 12"));
        }
    }

    @Test
    public void cambiarPinExigeLaClaveAnteriorYActualizaLaSesion() {
        SesionAdmin sesion = new SesionAdmin("monkey2026");

        try {
            sesion.cambiarPin("incorrecta", "nueva456");
            fail("Debe requerir la clave actual.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("anterior"));
        }

        sesion.cambiarPin("monkey2026", "nueva456");
        assertFalse(sesion.validarPin("monkey2026"));
        assertTrue(sesion.validarPin("nueva456"));
    }

    @Test
    public void cambiarPinRechazaClavesFueraDeFormato() {
        SesionAdmin sesion = new SesionAdmin("monkey2026");
        rechazarCambio(sesion, "corta");
        rechazarCambio(sesion, "1234567890123");
        rechazarCambio(sesion, "clave con espacio");
        assertTrue(sesion.validarPin("monkey2026"));
    }

    @Test
    public void cambiarPinAceptaAmbosLimitesDeLongitud() {
        SesionAdmin sesion = new SesionAdmin("monkey2026");
        sesion.cambiarPin("monkey2026", "ABC123");
        assertTrue(sesion.validarPin("ABC123"));
        sesion.cambiarPin("ABC123", "A1B2C3D4E5F6");
        assertTrue(sesion.validarPin("A1B2C3D4E5F6"));
    }

    private void rechazarCambio(SesionAdmin sesion, String nueva) {
        try {
            sesion.cambiarPin("monkey2026", nueva);
            fail("La clave no cumple el formato alfanumérico de 6 a 12 caracteres.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("6") || e.getMessage().contains("alfanuméricos"));
        }
    }

    @Test
    public void elHashPersistidoNoContieneLaClaveYSeConservaTrasReiniciar() throws Exception {
        File datos = carpeta.newFolder("credenciales");
        RepositorioPin repositorio = new RepositorioPin(datos);
        SesionAdmin sesion = new SesionAdmin("monkey2026", repositorio);

        String hashInicial = repositorio.leerConfiguracion();
        assertTrue(hashInicial.startsWith("pbkdf2-sha256$"));
        assertFalse(hashInicial.contains("monkey2026"));

        sesion.cambiarPin("monkey2026", "cambio789");
        SesionAdmin reiniciada = new SesionAdmin("monkey2026", repositorio);
        assertFalse(reiniciada.validarPin("monkey2026"));
        assertTrue(reiniciada.validarPin("cambio789"));
    }

    @Test
    public void unaConfiguracionHeredadaEnTextoPlanoSeMigraAHash() throws Exception {
        File datos = carpeta.newFolder("migracion");
        RepositorioPin repositorio = new RepositorioPin(datos);
        Files.write(new File(datos, "pin.conf").toPath(), "monkey2026".getBytes(StandardCharsets.UTF_8));

        SesionAdmin sesion = new SesionAdmin("monkey2026", repositorio);

        assertTrue(sesion.validarPin("monkey2026"));
        assertTrue(repositorio.leerConfiguracion().startsWith("pbkdf2-sha256$"));
    }

    @Test
    public void noSeReemplazaUnaConfiguracionCorruptaPorLaClaveInicial() throws Exception {
        File datos = carpeta.newFolder("corrupto");
        RepositorioPin repositorio = new RepositorioPin(datos);
        Files.write(new File(datos, "pin.conf").toPath(), "corta".getBytes(StandardCharsets.UTF_8));

        try {
            new SesionAdmin("monkey2026", repositorio);
            fail("La configuración inválida debe fallar sin sobrescribirse.");
        } catch (IllegalStateException e) {
            assertEquals("corta", repositorio.leerConfiguracion());
        }
    }

    @Test
    public void noSeReemplazaUnHashDesconocidoPorLaClaveInicial() throws Exception {
        File datos = carpeta.newFolder("hash-desconocido");
        RepositorioPin repositorio = new RepositorioPin(datos);
        String contenidoCorrupto = "pbkdf2-otro$100000$abc$def";
        Files.write(new File(datos, "pin.conf").toPath(), contenidoCorrupto.getBytes(StandardCharsets.UTF_8));

        try {
            new SesionAdmin("monkey2026", repositorio);
            fail("Un hash desconocido debe fallar cerrado.");
        } catch (IllegalStateException e) {
            assertEquals(contenidoCorrupto, repositorio.leerConfiguracion());
        }
    }
}
