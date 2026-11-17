package com.monkey.ui;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.monkey.logic.GeneradorReportes;
import com.monkey.logic.GestorAgenda;
import com.monkey.logic.GestorCatalogo;
import com.monkey.logic.GestorPOS;
import com.monkey.logic.SesionAdmin;

/**
 * Capa 1 - Ventana principal de MONKEY ELEGANT SYSTEM.
 *
 * Ensambla las tres pestañas del sistema (Agenda, Catalogo y Punto de Venta),
 * refresca la vista activa cada vez que el usuario cambia de pestaña (RNF 12)
 * y centraliza el control de acceso administrativo.
 */
public class VentanaPrincipal extends JFrame {

    private final JTabbedPane panelPestanas;
    private final VistaAgenda vistaAgenda;
    private final VistaCatalogo vistaCatalogo;
    private final VistaPOS vistaPOS;
    private final SesionAdmin sesionAdmin;

    public VentanaPrincipal(GestorAgenda gestorAgenda, GestorCatalogo gestorCatalogo,
            GestorPOS gestorPOS, GeneradorReportes generadorReportes, SesionAdmin sesionAdmin) {

        super("MONKEY ELEGANT SYSTEM - Gestion de Barberia");

        if (sesionAdmin == null) {
            throw new IllegalArgumentException("La sesion administrativa es obligatoria.");
        }
        this.sesionAdmin = sesionAdmin;

        this.vistaAgenda = new VistaAgenda(gestorAgenda, gestorCatalogo);
        this.vistaCatalogo = new VistaCatalogo(gestorCatalogo, this);
        this.vistaPOS = new VistaPOS(gestorPOS, generadorReportes, this);

        this.panelPestanas = new JTabbedPane();
        this.panelPestanas.addTab("Agenda", vistaAgenda);
        this.panelPestanas.addTab("Catalogo de Servicios", vistaCatalogo);
        this.panelPestanas.addTab("Punto de Venta", vistaPOS);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(panelPestanas, BorderLayout.CENTER);

        configurarCambioPestanas();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 700);
        setMinimumSize(new java.awt.Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    /**
     * Carga inicial de la primera pestaña y muestra la ventana.
     */
    public void iniciar() {
        vistaAgenda.refrescarHorarios();
        vistaAgenda.refrescarServicios();
        vistaAgenda.cargarTablaCitas();
        setVisible(true);
    }

    /**
     * Verifica la sesion administrativa vigente; si no existe, solicita el PIN.
     */
    public boolean verificarAccesoAdmin() {
        if (sesionAdmin.estaAutenticado()) {
            return true;
        }
        return DialogoPinAdmin.solicitarYValidar(this, sesionAdmin);
    }

    /**
     * Sincroniza los datos de la pestaña activa al cambiar de vista.
     */
    private void configurarCambioPestanas() {
        panelPestanas.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int indice = panelPestanas.getSelectedIndex();
                if (indice == 0) {
                    vistaAgenda.refrescarHorarios();
                    vistaAgenda.refrescarServicios();
                    vistaAgenda.cargarTablaCitas();
                } else if (indice == 1) {
                    vistaCatalogo.cargarTablaServicios();
                } else if (indice == 2) {
                    vistaPOS.buscarCitasActivas();
                }
            }
        });
    }

    public JTabbedPane getPanelPestanas() {
        return panelPestanas;
    }

    public SesionAdmin getSesionAdmin() {
        return sesionAdmin;
    }
}
