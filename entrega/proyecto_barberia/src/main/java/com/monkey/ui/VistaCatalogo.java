package com.monkey.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import com.monkey.logic.GestorCatalogo;
import com.monkey.model.servicios;

/**
 * Capa 1 - Pantalla de administracion del catalogo de servicios.
 *
 * Alta (RF 4), edicion (RF 5), baja logica (RF 6) y consulta aislada (RF 7).
 * Las operaciones de escritura exigen una sesion administrativa vigente
 * (matriz de accesos por rol).
 */
public class VistaCatalogo extends JPanel {

    private final GestorCatalogo gestorCatalogo;
    private final VentanaPrincipal ventanaPadre;

    private final JTextField txtNombre;
    private final JTextField txtPrecio;
    private final JComboBox<String> comboCategoria;
    private final JCheckBox chkActivo;
    private final JTextField txtFiltro;
    private final JCheckBox chkIncluirInactivos;
    private final JTable tablaServicios;

    private final DefaultTableModel modeloTabla;

    private final JButton btnGuardar;
    private final JButton btnLimpiar;
    private final JButton btnBuscar;
    private final JButton btnAlternarEstado;
    private final JButton btnCambiarPin;

    private final List<servicios> serviciosActuales = new ArrayList<>();
    private int idServicioSeleccionado = 0;

    public VistaCatalogo(GestorCatalogo gestorCatalogo, VentanaPrincipal ventanaPadre) {
        super(new BorderLayout(8, 8));

        if (gestorCatalogo == null) {
            throw new IllegalArgumentException("El gestor de catalogo es obligatorio.");
        }
        if (ventanaPadre == null) {
            throw new IllegalArgumentException("La ventana principal es obligatoria.");
        }
        this.gestorCatalogo = gestorCatalogo;
        this.ventanaPadre = ventanaPadre;

        this.txtNombre = new JTextField(18);
        this.txtPrecio = new JTextField(10);
        this.comboCategoria = new JComboBox<>(new DefaultComboBoxModel<>(gestorCatalogo.obtenerCategorias().toArray(new String[0])));
        this.chkActivo = new JCheckBox("Servicio activo", true);
        this.txtFiltro = new JTextField(15);
        this.chkIncluirInactivos = new JCheckBox("Ver inactivos", false);

        this.modeloTabla = new DefaultTableModel(
                new Object[] { "ID", "Nombre", "Precio", "Categoria", "Activo" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.tablaServicios = new JTable(modeloTabla);
        this.tablaServicios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        this.btnGuardar = new JButton("Guardar / Actualizar");
        this.btnLimpiar = new JButton("Limpiar Formulario");
        this.btnBuscar = new JButton("Filtrar");
        this.btnAlternarEstado = new JButton("Activar / Desactivar");
        this.btnCambiarPin = new JButton("Definir / Cambiar contraseña");

        btnGuardar.addActionListener(e -> {
            btnGuardar.setEnabled(false);
            try {
                onGuardarOActualizar();
            } finally {
                btnGuardar.setEnabled(true);
            }
        });
        btnLimpiar.addActionListener(e -> limpiarFormulario());
        btnBuscar.addActionListener(e -> {
            btnBuscar.setEnabled(false);
            try {
                cargarTablaServicios();
            } finally {
                btnBuscar.setEnabled(true);
            }
        });
        btnAlternarEstado.addActionListener(e -> {
            btnAlternarEstado.setEnabled(false);
            try {
                onAlternarEstado();
            } finally {
                btnAlternarEstado.setEnabled(true);
            }
        });
        btnCambiarPin.addActionListener(e -> {
            btnCambiarPin.setEnabled(false);
            try {
                DialogoPinAdmin.solicitarCambio(this, ventanaPadre.getSesionAdmin());
            } finally {
                btnCambiarPin.setEnabled(true);
            }
        });

        tablaServicios.getSelectionModel().addListSelectionListener(e -> onSeleccionarServicio());

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(crearPanelFormulario(), BorderLayout.NORTH);
        add(new JScrollPane(tablaServicios), BorderLayout.CENTER);
        add(crearPanelBotones(), BorderLayout.SOUTH);
    }

    private JPanel crearPanelFormulario() {
        JPanel panelFormulario = new JPanel(new GridLayout(3, 2, 6, 6));
        panelFormulario.add(new JLabel("Nombre del servicio:"));
        panelFormulario.add(txtNombre);
        panelFormulario.add(new JLabel("Precio unitario:"));
        panelFormulario.add(txtPrecio);
        panelFormulario.add(new JLabel("Categoria:"));
        panelFormulario.add(comboCategoria);

        JPanel panelOpciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelOpciones.add(chkActivo);
        panelOpciones.add(chkIncluirInactivos);
        panelOpciones.add(new JLabel("Filtrar por nombre:"));
        panelOpciones.add(txtFiltro);
        panelOpciones.add(btnBuscar);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(panelFormulario);
        panel.add(panelOpciones);
        return panel;
    }

    private JPanel crearPanelBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panel.add(btnGuardar);
        panel.add(btnAlternarEstado);
        panel.add(btnCambiarPin);
        panel.add(btnLimpiar);
        return panel;
    }

    /**
     * Carga la tabla del catalogo aplicando el filtro de nombre y el estado
     * (consulta aislada del modulo de agenda, RNF 3).
     */
    public void cargarTablaServicios() {
        String texto = txtFiltro.getText().trim();
        boolean soloActivos = !chkIncluirInactivos.isSelected();

        List<servicios> servicios = gestorCatalogo.filtrarServicios(texto, soloActivos);

        modeloTabla.setRowCount(0);
        serviciosActuales.clear();
        for (servicios servicio : servicios) {
            serviciosActuales.add(servicio);
            modeloTabla.addRow(new Object[] {
                    servicio.getIdServicio(),
                    servicio.getNombre(),
                    "$" + servicio.getCostoUnitario().toPlainString(),
                    servicio.getCategoria(),
                    servicio.isActivo() ? "Si" : "No"
            });
        }
        tablaServicios.clearSelection();
        idServicioSeleccionado = 0;
    }

    /**
     * Da de alta un servicio nuevo o actualiza el seleccionado (RF 4 y RF 5).
     */
    private void onGuardarOActualizar() {
        if (!ventanaPadre.verificarAccesoAdmin()) {
            return;
        }

        try {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) {
                throw new IllegalArgumentException("El nombre del servicio no puede estar vacio.");
            }

            BigDecimal precio = leerPrecio();
            String categoria = (String) comboCategoria.getSelectedItem();
            if (categoria == null) {
                throw new IllegalArgumentException("Debe seleccionar una categoria para el servicio.");
            }

            if (idServicioSeleccionado > 0) {
                servicios servicio = buscarServicioSeleccionado();
                if (servicio == null) {
                    throw new IllegalStateException("El servicio seleccionado ya no existe en el catalogo.");
                }
                servicio.setNombre(nombre);
                servicio.setCostoUnitario(precio);
                servicio.setCategoria(categoria);
                servicio.setActivo(chkActivo.isSelected());
                gestorCatalogo.editarServicio(servicio);
            } else {
                servicios nuevoServicio = new servicios(nombre, precio, categoria);
                nuevoServicio.setActivo(chkActivo.isSelected());
                gestorCatalogo.guardarServicio(nuevoServicio);
            }

            limpiarFormulario();
            cargarTablaServicios();
            JOptionPane.showMessageDialog(this,
                    "El servicio \"" + nombre + "\" se guardo correctamente.",
                    "Catalogo actualizado",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "El precio unitario debe ser un numero valido.",
                    "Precio no valido",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo guardar el servicio",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo guardar el servicio",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Activa o desactiva logicamente el servicio seleccionado (RF 6).
     */
    private void onAlternarEstado() {
        if (!ventanaPadre.verificarAccesoAdmin()) {
            return;
        }

        if (idServicioSeleccionado <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione un servicio de la tabla para cambiar su estado.",
                    "Sin seleccion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            servicios servicio = buscarServicioSeleccionado();
            if (servicio == null) {
                throw new IllegalStateException("El servicio seleccionado ya no existe en el catalogo.");
            }
            servicio.setActivo(!servicio.isActivo());
            gestorCatalogo.editarServicio(servicio);

            cargarTablaServicios();
            JOptionPane.showMessageDialog(this,
                    "El servicio \"" + servicio.getNombre() + "\" quedo "
                            + (servicio.isActivo() ? "activo." : "inactivo."),
                    "Catalogo actualizado",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo actualizar el servicio",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo actualizar el servicio",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Limpia el formulario y la seleccion de la tabla.
     */
    private void limpiarFormulario() {
        txtNombre.setText("");
        txtPrecio.setText("");
        comboCategoria.setSelectedIndex(0);
        chkActivo.setSelected(true);
        txtFiltro.setText("");
        chkIncluirInactivos.setSelected(false);
        tablaServicios.clearSelection();
        idServicioSeleccionado = 0;
    }

    private void onSeleccionarServicio() {
        int fila = tablaServicios.getSelectedRow();
        if (fila < 0 || fila >= serviciosActuales.size()) {
            idServicioSeleccionado = 0;
            return;
        }
        servicios servicio = serviciosActuales.get(fila);
        idServicioSeleccionado = servicio.getIdServicio();
        txtNombre.setText(servicio.getNombre());
        txtPrecio.setText(servicio.getCostoUnitario().toPlainString());
        comboCategoria.setSelectedItem(servicio.getCategoria());
        chkActivo.setSelected(servicio.isActivo());
    }

    private BigDecimal leerPrecio() {
        String texto = txtPrecio.getText().trim().replace(",", ".");
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Debe capturar el precio unitario del servicio.");
        }
        return new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
    }

    private servicios buscarServicioSeleccionado() {
        for (servicios servicio : serviciosActuales) {
            if (servicio.getIdServicio() == idServicioSeleccionado) {
                return servicio;
            }
        }
        return null;
    }
}
