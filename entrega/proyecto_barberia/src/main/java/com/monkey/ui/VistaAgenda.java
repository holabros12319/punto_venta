package com.monkey.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.table.DefaultTableModel;

import com.monkey.logic.GestorAgenda;
import com.monkey.logic.GestorCatalogo;
import com.monkey.model.cita;
import com.monkey.model.servicios;

/**
 * Capa 1 - Pantalla de agenda de citas.
 *
 * Permite consultar los horarios libres (RF 8), registrar una cita (RF 9),
 * cambiar su estado (RF 11 y RF 12) y buscarla por cliente (RF 13).
 * Toda la logica de negocio se delega en {@link GestorAgenda} y
 * {@link GestorCatalogo}.
 */
public class VistaAgenda extends JPanel {

    private final GestorAgenda gestorAgenda;
    private final GestorCatalogo gestorCatalogo;

    private final JTextField txtCliente;
    private final JSpinner selectorFecha;
    private final JComboBox<LocalTime> comboHorarios;
    private final JList<servicios> listaServicios;
    private final JTextField txtBuscar;
    private final JTable tablaCitas;

    private final DefaultListModel<servicios> modeloServicios;
    private final DefaultTableModel modeloTabla;

    private final JButton btnGuardar;
    private final JButton btnConfirmar;
    private final JButton btnCancelar;
    private final JButton btnLimpiar;
    private final JButton btnBuscar;

    private final List<cita> citasActuales = new ArrayList<>();
    private int idCitaSeleccionada = 0;

    public VistaAgenda(GestorAgenda gestorAgenda, GestorCatalogo gestorCatalogo) {
        super(new BorderLayout(8, 8));

        if (gestorAgenda == null || gestorCatalogo == null) {
            throw new IllegalArgumentException("Los gestores de agenda y catalogo son obligatorios.");
        }
        this.gestorAgenda = gestorAgenda;
        this.gestorCatalogo = gestorCatalogo;

        this.txtCliente = new JTextField(18);
        this.selectorFecha = new JSpinner(new SpinnerDateModel());
        this.selectorFecha.setEditor(new JSpinner.DateEditor(selectorFecha, "yyyy-MM-dd"));
        this.comboHorarios = new JComboBox<>();
        this.modeloServicios = new DefaultListModel<>();
        this.listaServicios = new JList<>(modeloServicios);
        this.listaServicios.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.txtBuscar = new JTextField(18);

        this.modeloTabla = new DefaultTableModel(
                new Object[] { "ID", "Cliente", "Fecha", "Hora inicio", "Hora término", "Servicios", "Total", "Estado" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.tablaCitas = new JTable(modeloTabla);
        this.tablaCitas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        this.btnGuardar = new JButton("Guardar Cita");
        this.btnConfirmar = new JButton("Marcar Confirmada");
        this.btnCancelar = new JButton("Marcar Cancelada");
        this.btnLimpiar = new JButton("Limpiar Formulario");
        this.btnBuscar = new JButton("Buscar Citas");

        btnGuardar.addActionListener(e -> {
            btnGuardar.setEnabled(false);
            try {
                onGuardarCita();
            } finally {
                btnGuardar.setEnabled(true);
            }
        });
        btnConfirmar.addActionListener(e -> {
            btnConfirmar.setEnabled(false);
            try {
                onCambiarEstado("Confirmada");
            } finally {
                btnConfirmar.setEnabled(true);
            }
        });
        btnCancelar.addActionListener(e -> {
            btnCancelar.setEnabled(false);
            try {
                onCambiarEstado("Cancelada");
            } finally {
                btnCancelar.setEnabled(true);
            }
        });
        btnLimpiar.addActionListener(e -> limpiarFormulario());
        btnBuscar.addActionListener(e -> {
            btnBuscar.setEnabled(false);
            try {
                cargarTablaCitas();
            } finally {
                btnBuscar.setEnabled(true);
            }
        });

        tablaCitas.getSelectionModel().addListSelectionListener(e -> onSeleccionarCita());

        // El texto completo del servicio se muestra como tooltip cuando la
        // columna del selector es mas estrecha que el nombre completo.
        listaServicios.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int indice = listaServicios.locationToIndex(e.getPoint());
                if (indice >= 0) {
                    listaServicios.setToolTipText(String.valueOf(listaServicios.getModel().getElementAt(indice)));
                } else {
                    listaServicios.setToolTipText(null);
                }
            }
        });

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(crearPanelSuperior(), BorderLayout.NORTH);
        add(crearPanelServicios(), BorderLayout.WEST);
        add(new JScrollPane(tablaCitas), BorderLayout.CENTER);
        add(crearPanelBotones(), BorderLayout.SOUTH);
    }

    private JPanel crearPanelSuperior() {
        JPanel panelFormulario = new JPanel(new GridLayout(3, 2, 6, 6));
        panelFormulario.add(new JLabel("Cliente:"));
        panelFormulario.add(txtCliente);
        panelFormulario.add(new JLabel("Fecha:"));
        panelFormulario.add(selectorFecha);
        panelFormulario.add(new JLabel("Horario:"));
        panelFormulario.add(comboHorarios);

        JPanel panelBuscar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panelBuscar.add(new JLabel("Buscar por cliente:"));
        panelBuscar.add(txtBuscar);
        panelBuscar.add(btnBuscar);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(panelFormulario);
        panel.add(panelBuscar);
        return panel;
    }

    private JPanel crearPanelServicios() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Servicios (seleccion multiple)"));
        panel.add(new JScrollPane(listaServicios), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(260, 300));
        return panel;
    }

    private JPanel crearPanelBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panel.add(btnGuardar);
        panel.add(btnConfirmar);
        panel.add(btnCancelar);
        panel.add(btnLimpiar);
        return panel;
    }

    /**
     * Recarga los horarios libres de la fecha seleccionada (RF 8).
     */
    public void refrescarHorarios() {
        LocalTime seleccionPrevia = (LocalTime) comboHorarios.getSelectedItem();

        comboHorarios.removeAllItems();
        List<LocalTime> disponibles = gestorAgenda.obtenerHorariosDisponibles(obtenerFechaSeleccionada());
        for (LocalTime horario : disponibles) {
            comboHorarios.addItem(horario);
        }
        if (seleccionPrevia != null && disponibles.contains(seleccionPrevia)) {
            comboHorarios.setSelectedItem(seleccionPrevia);
        }
    }

    /**
     * Recarga el selector de servicios con los servicios activos del catalogo (RF 6).
     */
    public void refrescarServicios() {
        modeloServicios.clear();
        List<servicios> servicios = gestorCatalogo.filtrarServicios("", true);
        for (servicios servicio : servicios) {
            modeloServicios.addElement(servicio);
        }
    }

    /**
     * Carga la tabla de citas aplicando el filtro parcial por cliente (RF 13).
     */
    public void cargarTablaCitas() {
        String filtro = txtBuscar.getText().trim();

        List<cita> citas = gestorAgenda.buscarCitas(filtro, null);

        modeloTabla.setRowCount(0);
        citasActuales.clear();
        for (cita item : citas) {
            citasActuales.add(item);
            modeloTabla.addRow(new Object[] {
                    item.getIdCita(),
                    item.getNombreCliente(),
                    item.getFecha().toString(),
                    item.getHora().toString(),
                    item.getHoraFin().toString(),
                    describirServicios(item),
                    "$" + item.calcularCostoTotal().toPlainString(),
                    item.getEstado()
            });
        }
        tablaCitas.clearSelection();
        idCitaSeleccionada = 0;
    }

    /**
     * Registra la cita capturada en el formulario (RF 9).
     */
    private void onGuardarCita() {
        try {
            String cliente = txtCliente.getText().trim();
            if (cliente.isEmpty()) {
                throw new IllegalArgumentException("Debe capturar el nombre del cliente.");
            }

            LocalTime horario = (LocalTime) comboHorarios.getSelectedItem();
            if (horario == null) {
                throw new IllegalArgumentException("No hay horarios disponibles para la fecha seleccionada.");
            }

            List<servicios> serviciosSeleccionados = listaServicios.getSelectedValuesList();
            if (serviciosSeleccionados.isEmpty()) {
                throw new IllegalArgumentException("Debe seleccionar al menos un servicio para la cita.");
            }

            cita nuevaCita = new cita(cliente, obtenerFechaSeleccionada(), horario, serviciosSeleccionados);
            gestorAgenda.registrarCita(nuevaCita);

            limpiarFormulario();
            refrescarHorarios();
            cargarTablaCitas();

            JOptionPane.showMessageDialog(this,
                    "La cita de " + cliente + " quedo registrada el " + nuevaCita.getFecha() + " de "
                            + nuevaCita.getHora() + " a " + nuevaCita.getHoraFin() + ".",
                    "Cita registrada",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo registrar la cita",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo registrar la cita",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Cambia el estado de la cita seleccionada (RF 11). Cancelar libera el
     * horario bloqueado (RF 12).
     */
    private void onCambiarEstado(String estado) {
        if (idCitaSeleccionada <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione una cita de la tabla para cambiar su estado.",
                    "Sin seleccion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            gestorAgenda.cambiarEstadoCita(idCitaSeleccionada, estado);
            cargarTablaCitas();
            refrescarHorarios();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo actualizar la cita",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo actualizar la cita",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Limpia el formulario y la seleccion de la tabla.
     */
    private void limpiarFormulario() {
        txtCliente.setText("");
        txtBuscar.setText("");
        comboHorarios.setSelectedIndex(-1);
        listaServicios.clearSelection();
        tablaCitas.clearSelection();
        idCitaSeleccionada = 0;
        refrescarHorarios();
    }

    private void onSeleccionarCita() {
        int fila = tablaCitas.getSelectedRow();
        if (fila < 0 || fila >= citasActuales.size()) {
            idCitaSeleccionada = 0;
            return;
        }
        idCitaSeleccionada = citasActuales.get(fila).getIdCita();
    }

    private LocalDate obtenerFechaSeleccionada() {
        long milisegundos = ((SpinnerDateModel) selectorFecha.getModel()).getDate().getTime();
        return Instant.ofEpochMilli(milisegundos).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String describirServicios(cita item) {
        StringBuilder descripcion = new StringBuilder();
        for (servicios servicio : item.getServicios()) {
            if (descripcion.length() > 0) {
                descripcion.append(" + ");
            }
            descripcion.append(servicio.getNombre().trim());
        }
        return descripcion.toString();
    }
}
