package com.monkey.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.awt.GraphicsEnvironment;

import org.junit.Before;
import org.junit.Test;

import com.monkey.logic.SesionAdmin;

/**
 * Capa 1 - Pruebas del dialogo de PIN administrativo.
 * Verifica el enmascaramiento de la credencial (RNF 1) y que la validacion se
 * delega en la sesion administrativa. Requiere un entorno grafico.
 */
public class DialogoPinAdminTest {

    private SesionAdmin sesion;

    @Before
    public void setUp() {
        assumeFalse("Requiere un entorno grafico disponible.", GraphicsEnvironment.isHeadless());
        sesion = new SesionAdmin("monkey2026");
    }

    @Test
    public void elDialogoCapturaElPinEnUnCampoOculto() throws Exception {
        DialogoPinAdmin dialogo = new DialogoPinAdmin(null, sesion);

        java.lang.reflect.Field campo = DialogoPinAdmin.class.getDeclaredField("campoPin");
        campo.setAccessible(true);
        assertTrue(campo.get(dialogo) instanceof javax.swing.JPasswordField);
    }

    @Test
    public void elDialogoIniciaSinConfirmar() {
        DialogoPinAdmin dialogo = new DialogoPinAdmin(null, sesion);
        assertFalse(dialogo.isConfirmado());
    }

    @Test
    public void elDialogoExigeUnaSesionAdministrativa() {
        try {
            new DialogoPinAdmin(null, null);
            org.junit.Assert.fail("La sesion administrativa es obligatoria.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("obligatoria"));
        }
    }

    @Test
    public void elPinCorrectoValidaLaSesionDelSistema() {
        assertTrue(sesion.validarPin("monkey2026"));
        assertTrue(sesion.estaAutenticado());
        assertEquals("monkey2026", "monkey2026");
    }

    @Test
    public void elDialogoDeCambioOcultaClaveAnteriorNuevaYConfirmacion() throws Exception {
        DialogoPinAdmin dialogo = new DialogoPinAdmin(null, sesion, true);

        assertTrue(leerCampo(dialogo, "campoPinAnterior") instanceof javax.swing.JPasswordField);
        assertTrue(leerCampo(dialogo, "campoPinNuevo") instanceof javax.swing.JPasswordField);
        assertTrue(leerCampo(dialogo, "campoPinConfirmacion") instanceof javax.swing.JPasswordField);
    }

    private Object leerCampo(Object objetivo, String nombre) throws Exception {
        java.lang.reflect.Field campo = objetivo.getClass().getDeclaredField(nombre);
        campo.setAccessible(true);
        return campo.get(objetivo);
    }
}
