package com.monkey.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import com.monkey.logic.GeneradorReportes;
import com.monkey.logic.GestorPOS;
import com.monkey.model.cita;
import com.monkey.model.servicios;
import com.monkey.model.venta;

/**
 * Capa 1 - Pantalla de punto de venta.
 *
 * Importa las citas cobrables (RF 14), calcula el cambio en vivo (RF 15),
 * registra la venta y muestra el comprobante en pantalla (RF 16 y RF 17) y
 * exporta los reportes financieros en CSV (RF 18).
 */
public class VistaPOS extends JPanel {

    private static final String NOMBRE_ESTABLECIMIENTO = "MONKEY ELEGANT";

    private final GestorPOS gestorPOS;
    private final GeneradorReportes generadorReportes;
    private final VentanaPrincipal ventanaPadre;

    private final JTextField txtBuscar;
    private final JTable tablaPendientes;
    private final JLabel lblTotal;
    private final JTextField txtEfectivo;
    private final JLabel lblCambio;
    private final JButton btnCobrar;

    private final DefaultTableModel modeloTabla;

    private final List<cita> citasPendientesActuales = new ArrayList<>();
    private cita citaActiva;

    public VistaPOS(GestorPOS gestorPOS, GeneradorReportes generadorReportes, VentanaPrincipal ventanaPadre) {
        super(new BorderLayout(8, 8));

        if (gestorPOS == null || generadorReportes == null) {
            throw new IllegalArgumentException("El gestor de punto de venta y el generador de reportes son obligatorios.");
        }
        if (ventanaPadre == null) {
            throw new IllegalArgumentException("La ventana principal es obligatoria.");
        }
        this.gestorPOS = gestorPOS;
        this.generadorReportes = generadorReportes;
        this.ventanaPadre = ventanaPadre;

        this.txtBuscar = new JTextField(18);
        this.modeloTabla = new DefaultTableModel(
                new Object[] { "ID", "Cliente", "Fecha", "Hora", "Servicios", "Total" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.tablaPendientes = new JTable(modeloTabla);
        this.tablaPendientes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        this.lblTotal = new JLabel("$0.00");
        this.lblTotal.setFont(lblTotal.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        this.txtEfectivo = new JTextField(10);
        this.lblCambio = new JLabel("$0.00");
        this.lblCambio.setFont(lblCambio.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        this.btnCobrar = new JButton("Cobrar");

        JButton btnBuscar = new JButton("Buscar Citas");
        JButton btnExportarDetallado = new JButton("Exportar CSV Detallado");
        JButton btnExportarAcumulado = new JButton("Exportar CSV Acumulado");

        btnBuscar.addActionListener(e -> {
            btnBuscar.setEnabled(false);
            try {
                buscarCitasActivas();
            } finally {
                btnBuscar.setEnabled(true);
            }
        });
        btnCobrar.addActionListener(e -> {
            btnCobrar.setEnabled(false);
            try {
                onProcesarCobro();
            } finally {
                btnCobrar.setEnabled(true);
            }
        });
        btnExportarDetallado.addActionListener(e -> {
            btnExportarDetallado.setEnabled(false);
            try {
                onExportarCSV(true);
            } finally {
                btnExportarDetallado.setEnabled(true);
            }
        });
        btnExportarAcumulado.addActionListener(e -> {
            btnExportarAcumulado.setEnabled(false);
            try {
                onExportarCSV(false);
            } finally {
                btnExportarAcumulado.setEnabled(true);
            }
        });

        txtEfectivo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onActualizarCambioEnVivo();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onActualizarCambioEnVivo();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onActualizarCambioEnVivo();
            }
        });

        tablaPendientes.getSelectionModel().addListSelectionListener(e -> onSeleccionarCita());

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(crearPanelBusqueda(btnBuscar), BorderLayout.NORTH);
        add(new JScrollPane(tablaPendientes), BorderLayout.CENTER);
        add(crearPanelCobro(btnExportarDetallado, btnExportarAcumulado), BorderLayout.SOUTH);
    }

    private JPanel crearPanelBusqueda(JButton btnBuscar) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.add(new JLabel("Buscar citas por cliente:"));
        panel.add(txtBuscar);
        panel.add(btnBuscar);
        return panel;
    }

    private JPanel crearPanelCobro(JButton btnExportarDetallado, JButton btnExportarAcumulado) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));

        JPanel panelDatos = new JPanel(new GridLayout(3, 2, 6, 6));
        panelDatos.add(new JLabel("Total a cobrar:"));
        panelDatos.add(lblTotal);
        panelDatos.add(new JLabel("Efectivo recibido:"));
        panelDatos.add(txtEfectivo);
        panelDatos.add(new JLabel("Cambio a entregar:"));
        panelDatos.add(lblCambio);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        panelBotones.add(btnCobrar);
        panelBotones.add(btnExportarDetallado);
        panelBotones.add(btnExportarAcumulado);

        panel.add(panelDatos, BorderLayout.CENTER);
        panel.add(panelBotones, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(600, 150));
        return panel;
    }

    /**
     * Carga las citas pendientes y confirmadas que aun no fueron cobradas (RF 14).
     */
    public void buscarCitasActivas() {
        String filtro = txtBuscar.getText().trim();

        List<cita> cobrables = gestorPOS.buscarCitasPorCobrar(filtro);

        modeloTabla.setRowCount(0);
        citasPendientesActuales.clear();
        for (cita item : cobrables) {
            citasPendientesActuales.add(item);
            modeloTabla.addRow(new Object[] {
                    item.getIdCita(),
                    item.getNombreCliente(),
                    item.getFecha().toString(),
                    item.getHora().toString(),
                    describirServicios(item),
                    "$" + item.calcularCostoTotal().toPlainString()
            });
        }
        tablaPendientes.clearSelection();
        citaActiva = null;
        lblTotal.setText("$0.00");
        lblCambio.setText("$0.00");
    }

    /**
     * Carga la cita seleccionada en el panel de cobro.
     */
    private void onSeleccionarCita() {
        int fila = tablaPendientes.getSelectedRow();
        if (fila < 0 || fila >= citasPendientesActuales.size()) {
            citaActiva = null;
            lblTotal.setText("$0.00");
            lblCambio.setText("$0.00");
            return;
        }
        citaActiva = citasPendientesActuales.get(fila);
        lblTotal.setText("$" + citaActiva.calcularCostoTotal().toPlainString());
        lblCambio.setText("$0.00");
    }

    /**
     * Calcula el cambio mientras se escribe el efectivo. Si el campo esta
     * vacio o incompleto se muestra $0.00 sin interrumpir al usuario.
     */
    private void onActualizarCambioEnVivo() {
        if (citaActiva == null) {
            lblCambio.setText("$0.00");
            return;
        }

        String texto = txtEfectivo.getText().trim();
        if (texto.isEmpty()) {
            lblCambio.setText("$0.00");
            return;
        }

        BigDecimal efectivo;
        try {
            efectivo = new BigDecimal(texto.replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            lblCambio.setText("$0.00");
            return;
        }

        try {
            BigDecimal total = citaActiva.calcularCostoTotal();
            BigDecimal cambio = gestorPOS.calcularCambio(total, efectivo);
            lblCambio.setText("$" + cambio.toPlainString());
        } catch (IllegalArgumentException e) {
            lblCambio.setText("$0.00");
        }
    }

    /**
     * Procesa el cobro de la cita seleccionada y muestra el comprobante (RF 16 y RF 17).
     */
    private void onProcesarCobro() {
        if (citaActiva == null) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione una cita de la tabla para cobrar.",
                    "Sin seleccion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String texto = txtEfectivo.getText().trim();
            if (texto.isEmpty()) {
                throw new IllegalArgumentException("Debe capturar el efectivo recibido.");
            }
            BigDecimal efectivo = new BigDecimal(texto.replace(",", ".")).setScale(2, RoundingMode.HALF_UP);

            venta ventaRegistrada = gestorPOS.procesarCobro(citaActiva.getIdCita(), efectivo);
            String comprobante = construirComprobante(ventaRegistrada, citaActiva);

            txtEfectivo.setText("");
            lblCambio.setText("$0.00");
            buscarCitasActivas();

            JOptionPane.showMessageDialog(this, comprobante, "Comprobante de venta",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "El efectivo recibido debe ser un numero valido.",
                    "Efectivo no valido",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo procesar el cobro",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo procesar el cobro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Exporta los reportes financieros en CSV (RF 18). La exportacion exige
     * una sesion administrativa vigente.
     */
    private void onExportarCSV(boolean detallado) {
        if (!ventanaPadre.verificarAccesoAdmin()) {
            return;
        }

        try {
            File destino;
            if (detallado) {
                LocalDate[] rango = solicitarRangoFechas();
                if (rango == null) {
                    return;
                }
                destino = elegirDestino("ventas_detallado.csv");
                if (destino == null) {
                    return;
                }
                generadorReportes.exportarDetallado(destino, rango[0], rango[1]);
            } else {
                YearMonth periodo = solicitarPeriodo();
                if (periodo == null) {
                    return;
                }
                destino = elegirDestino("ingresos_" + periodo.toString() + ".csv");
                if (destino == null) {
                    return;
                }
                generadorReportes.exportarAcumuladoMensual(destino, periodo);
            }

            if (destino != null) {
                JOptionPane.showMessageDialog(this,
                        "El reporte se guardo en:\n" + destino.getAbsolutePath(),
                        "Exportacion completada",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo exportar el reporte",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo exportar el reporte",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Comprobante visual en pantalla tras el cobro (RF 17).
     */
    private String construirComprobante(venta ventaRegistrada, cita citaCobrada) {
        BigDecimal total = ventaRegistrada.getTotal();
        BigDecimal efectivo = ventaRegistrada.getEfectivo();
        BigDecimal cambio = ventaRegistrada.getCambio();

        StringBuilder comprobante = new StringBuilder();
        comprobante.append(NOMBRE_ESTABLECIMIENTO).append("\n");
        comprobante.append("Comprobante de venta\n");
        comprobante.append("------------------------------\n");
        comprobante.append("Venta N. ").append(obtenerIdentificadorVenta(citaCobrada, ventaRegistrada)).append("\n");
        comprobante.append("Fecha y hora: ").append(ventaRegistrada.getFechaHora()).append("\n");
        comprobante.append("Cliente: ").append(citaCobrada.getNombreCliente()).append("\n");
        comprobante.append("Servicios:\n");
        for (servicios servicio : citaCobrada.getServicios()) {
            comprobante.append("  - ").append(servicio.getNombre().trim())
                    .append("  $").append(servicio.getCostoUnitario().toPlainString()).append("\n");
        }
        comprobante.append("------------------------------\n");
        comprobante.append("Total cobrado: $").append(total.toPlainString()).append("\n");
        comprobante.append("Efectivo recibido: $").append(efectivo.toPlainString()).append("\n");
        comprobante.append("Cambio: $").append(cambio.toPlainString()).append("\n");
        return comprobante.toString();
    }

    /**
     * El identificador real de la venta se toma del registro ya persistido.
     */
    private String obtenerIdentificadorVenta(cita citaCobrada, venta ventaRegistrada) {
        try {
            venta persistida = gestorPOS.obtenerVentaRegistrada(citaCobrada.getIdCita());
            if (persistida != null && persistida.getIdVenta() > 0) {
                return String.valueOf(persistida.getIdVenta());
            }
        } catch (RuntimeException e) {
            System.err.println("Aviso al consultar el identificador de la venta: " + e.getMessage());
        }
        return "cita " + citaCobrada.getIdCita();
    }

    /**
     * Solicita el rango de fechas del reporte detallado.
     */
    private LocalDate[] solicitarRangoFechas() {
        JSpinner spinnerDesde = new JSpinner(new SpinnerDateModel());
        spinnerDesde.setEditor(new JSpinner.DateEditor(spinnerDesde, "yyyy-MM-dd"));
        JSpinner spinnerHasta = new JSpinner(new SpinnerDateModel());
        spinnerHasta.setEditor(new JSpinner.DateEditor(spinnerHasta, "yyyy-MM-dd"));

        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        panel.add(new JLabel("Fecha inicial:"));
        panel.add(spinnerDesde);
        panel.add(new JLabel("Fecha final:"));
        panel.add(spinnerHasta);

        int opcion = JOptionPane.showConfirmDialog(this, panel,
                "Reporte detallado de ventas", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) {
            return null;
        }

        LocalDate desde = leerFecha(spinnerDesde);
        LocalDate hasta = leerFecha(spinnerHasta);
        if (desde.isAfter(hasta)) {
            JOptionPane.showMessageDialog(this,
                    "La fecha inicial no puede ser posterior a la fecha final.",
                    "Rango no valido",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return new LocalDate[] { desde, hasta };
    }

    /**
     * Solicita el periodo mensual del reporte acumulado.
     */
    private YearMonth solicitarPeriodo() {
        List<YearMonth> periodos = generadorReportes.obtenerPeriodosDisponibles();

        List<String> opciones = new ArrayList<>();
        for (YearMonth periodo : periodos) {
            opciones.add(periodo.toString());
        }
        if (opciones.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay ventas registradas para exportar.",
                    "Sin datos",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        JComboBox<String> combo = new JComboBox<>(opciones.toArray(new String[0]));
        JPanel panel = new JPanel(new GridLayout(1, 2, 6, 6));
        panel.add(new JLabel("Periodo (ano-mes):"));
        panel.add(combo);

        int opcion = JOptionPane.showConfirmDialog(this, panel,
                "Reporte acumulado de ingresos", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) {
            return null;
        }
        return YearMonth.parse((String) combo.getSelectedItem());
    }

    /**
     * Selector de archivo de destino del reporte CSV.
     */
    private File elegirDestino(String nombreSugerido) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar reporte CSV");
        selector.setSelectedFile(new File(nombreSugerido));
        selector.setFileFilter(new FileNameExtensionFilter("Archivos CSV (*.csv)", "csv"));

        int opcion = selector.showSaveDialog(this);
        if (opcion != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File archivo = selector.getSelectedFile();
        if (archivo == null) {
            return null;
        }
        String nombre = archivo.getName().toLowerCase();
        if (!nombre.endsWith(".csv")) {
            archivo = new File(archivo.getAbsolutePath() + ".csv");
        }
        return archivo;
    }

    private LocalDate leerFecha(JSpinner spinner) {
        long milisegundos = ((SpinnerDateModel) spinner.getModel()).getDate().getTime();
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
