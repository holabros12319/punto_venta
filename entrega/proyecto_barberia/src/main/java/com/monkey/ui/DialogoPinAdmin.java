package com.monkey.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

import com.monkey.logic.SesionAdmin;

/** Diálogos modales de acceso y cambio de credencial administrativa. */
public class DialogoPinAdmin extends JDialog {

    private final JPasswordField campoPin;
    private final JPasswordField campoPinAnterior;
    private final JPasswordField campoPinNuevo;
    private final JPasswordField campoPinConfirmacion;
    private final SesionAdmin sesion;
    private final boolean modoCambio;
    private boolean confirmado;

    public DialogoPinAdmin(Component padre, SesionAdmin sesion) {
        this(padre, sesion, false);
    }

    /** Constructor visible para permitir inspección y pruebas del modo cambio. */
    public DialogoPinAdmin(Component padre, SesionAdmin sesion, boolean modoCambio) {
        super(resolverVentanaPadre(padre), modoCambio ? "Cambiar contraseña administrativa" : "Acceso Administrativo",
                Dialog.ModalityType.APPLICATION_MODAL);

        if (sesion == null) {
            throw new IllegalArgumentException("La sesión administrativa es obligatoria.");
        }
        this.sesion = sesion;
        this.modoCambio = modoCambio;
        this.confirmado = false;

        JPanel panelCentro;
        if (modoCambio) {
            campoPin = null;
            campoPinAnterior = new JPasswordField(12);
            campoPinNuevo = new JPasswordField(12);
            campoPinConfirmacion = new JPasswordField(12);
            panelCentro = new JPanel(new GridLayout(3, 2, 6, 6));
            panelCentro.add(new JLabel("Contraseña anterior:"));
            panelCentro.add(campoPinAnterior);
            panelCentro.add(new JLabel("Nueva contraseña (6 a 12 caracteres):"));
            panelCentro.add(campoPinNuevo);
            panelCentro.add(new JLabel("Confirmar nueva contraseña:"));
            panelCentro.add(campoPinConfirmacion);
        } else {
            campoPin = new JPasswordField(12);
            campoPinAnterior = null;
            campoPinNuevo = null;
            campoPinConfirmacion = null;
            panelCentro = new JPanel(new FlowLayout(FlowLayout.CENTER));
            panelCentro.add(new JLabel("Ingrese la contraseña de administrador:"));
            panelCentro.add(campoPin);
        }

        JButton btnAceptar = new JButton(modoCambio ? "Guardar contraseña" : "Aceptar");
        JButton btnCancelar = new JButton("Cancelar");
        btnAceptar.addActionListener(e -> {
            btnAceptar.setEnabled(false);
            try {
                if (modoCambio) {
                    onAceptarCambio();
                } else {
                    onAceptar();
                }
            } finally {
                btnAceptar.setEnabled(true);
            }
        });
        btnCancelar.addActionListener(e -> {
            btnCancelar.setEnabled(false);
            try {
                onCancelar();
            } finally {
                btnCancelar.setEnabled(true);
            }
        });

        JPanel panelSur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelSur.add(btnCancelar);
        panelSur.add(btnAceptar);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(panelCentro, BorderLayout.CENTER);
        getContentPane().add(panelSur, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btnAceptar);
        pack();
        setResizable(false);
        setLocationRelativeTo(padre);
    }

    private static Window resolverVentanaPadre(Component padre) {
        if (padre == null) {
            return null;
        }
        return SwingUtilities.getWindowAncestor(padre);
    }

    public static boolean solicitarYValidar(Component padre, SesionAdmin sesion) {
        DialogoPinAdmin dialogo = new DialogoPinAdmin(padre, sesion);
        dialogo.setVisible(true);
        return dialogo.confirmado;
    }

    public static boolean solicitarCambio(Component padre, SesionAdmin sesion) {
        DialogoPinAdmin dialogo = new DialogoPinAdmin(padre, sesion, true);
        dialogo.setVisible(true);
        return dialogo.confirmado;
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    private void onAceptar() {
        char[] caracteres = campoPin.getPassword();
        try {
            if (sesion.validarPin(caracteres)) {
                confirmado = true;
                dispose();
                return;
            }
            JOptionPane.showMessageDialog(this,
                    "La contraseña ingresada no es correcta. La acción fue denegada.",
                    "Acceso denegado", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(caracteres, '\0');
            campoPin.setText("");
        }
    }

    private void onAceptarCambio() {
        char[] anterior = campoPinAnterior.getPassword();
        char[] nueva = campoPinNuevo.getPassword();
        char[] confirmacion = campoPinConfirmacion.getPassword();
        try {
            if (!Arrays.equals(nueva, confirmacion)) {
                JOptionPane.showMessageDialog(this,
                        "La confirmación no coincide con la nueva contraseña.",
                        "Confirmación incorrecta", JOptionPane.ERROR_MESSAGE);
                return;
            }
            sesion.cambiarPin(anterior, nueva);
            confirmado = true;
            JOptionPane.showMessageDialog(this,
                    "La contraseña administrativa se actualizó correctamente.",
                    "Contraseña actualizada", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo cambiar la contraseña", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "No se pudo guardar la contraseña", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(anterior, '\0');
            Arrays.fill(nueva, '\0');
            Arrays.fill(confirmacion, '\0');
            campoPinAnterior.setText("");
            campoPinNuevo.setText("");
            campoPinConfirmacion.setText("");
        }
    }

    private void onCancelar() {
        if (modoCambio) {
            campoPinAnterior.setText("");
            campoPinNuevo.setText("");
            campoPinConfirmacion.setText("");
        } else {
            campoPin.setText("");
        }
        confirmado = false;
        dispose();
    }
}
