package com.monkey.logic;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.monkey.data_persistance.GeneradorCSV;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.model.cita;
import com.monkey.model.venta;

/**
 * Capa 2 - Generacion de reportes financieros en CSV (RF 18).
 *
 * Dos modalidades independientes:
 *  - Reporte detallado de ventas: un renglon por ticket.
 *  - Reporte acumulado de ingresos: contabilizacion diaria agrupada por mes.
 *
 * Las lineas se arman con {@link GeneradorCSV#construirFila(String...)}, que
 * aplica el escape RFC 4180 y neutraliza celdas que inicien con =, +, - o @
 * (RNF 13). El archivo se escribe en UTF-8 con marca BOM para Excel.
 */
public class GeneradorReportes {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RepositorioVentas repoVentas;
    private final GeneradorCSV escritorCSV;

    public GeneradorReportes(RepositorioVentas repoVentas, GeneradorCSV escritorCSV) {
        if (repoVentas == null) {
            throw new IllegalArgumentException("El repositorio de ventas es obligatorio.");
        }
        if (escritorCSV == null) {
            throw new IllegalArgumentException("El escritor de archivos CSV es obligatorio.");
        }
        this.repoVentas = repoVentas;
        this.escritorCSV = escritorCSV;
    }

    /**
     * Exporta el reporte detallado de todas las ventas registradas.
     */
    public void exportarDetallado(File destino) {
        exportarDetallado(destino, null, null);
    }

    /**
     * Exporta el reporte detallado de las ventas registradas en el rango de
     * fechas indicado (ambos limites inclusive).
     */
    public void exportarDetallado(File destino, LocalDate desde, LocalDate hasta) {
        if (destino == null) {
            throw new IllegalArgumentException("Debe indicar la ruta del archivo CSV de destino.");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha inicial del reporte no puede ser posterior a la fecha final.");
        }

        List<venta> ventas = repoVentas.listarPorRangoFechas(desde, hasta);
        validarDatosNoVacios(ventas);

        List<String> lineas = new ArrayList<>();
        lineas.add(escritorCSV.construirFila("ID Venta", "Fecha y Hora", "Cliente",
                "Servicios Cobrados", "Total", "Efectivo", "Cambio"));

        for (venta venta : ventas) {
            lineas.add(escritorCSV.construirFila(
                    String.valueOf(venta.getIdVenta()),
                    venta.getFechaHora().format(FORMATO_FECHA_HORA),
                    obtenerNombreCliente(venta),
                    venta.getSnapshotServicios(),
                    venta.getTotal().toPlainString(),
                    venta.getEfectivo().toPlainString(),
                    venta.getCambio().toPlainString()));
        }

        escritorCSV.escribirLineas(destino, lineas);
    }

    /**
     * Exporta el reporte acumulado de ingresos de todos los periodos con ventas.
     */
    public void exportarAcumuladoMensual(File destino) {
        exportarAcumuladoMensual(destino, null);
    }

    /**
     * Exporta el reporte acumulado de ingresos de un periodo mensual concreto.
     * Cada renglon contabiliza las ventas de un dia dentro del periodo.
     */
    public void exportarAcumuladoMensual(File destino, YearMonth periodo) {
        if (destino == null) {
            throw new IllegalArgumentException("Debe indicar la ruta del archivo CSV de destino.");
        }

        List<venta> ventas = repoVentas.listarPorPeriodo(periodo);
        validarDatosNoVacios(ventas);

        // Agrupacion por periodo (YearMonth) y luego por dia (LocalDate).
        Map<YearMonth, Map<LocalDate, List<venta>>> agrupado = new LinkedHashMap<>();
        for (venta venta : ventas) {
            YearMonth mes = YearMonth.from(venta.getFechaHora());
            LocalDate dia = venta.getFechaHora().toLocalDate();

            Map<LocalDate, List<venta>> porDia = agrupado.get(mes);
            if (porDia == null) {
                porDia = new LinkedHashMap<>();
                agrupado.put(mes, porDia);
            }
            List<venta> ventasDelDia = porDia.get(dia);
            if (ventasDelDia == null) {
                ventasDelDia = new ArrayList<>();
                porDia.put(dia, ventasDelDia);
            }
            ventasDelDia.add(venta);
        }

        List<String> lineas = new ArrayList<>();
        lineas.add(escritorCSV.construirFila("Periodo (Año-Mes)", "Fecha", "Cantidad de Ventas", "Total Diario"));

        for (Map.Entry<YearMonth, Map<LocalDate, List<venta>>> entradaMes : agrupado.entrySet()) {
            for (Map.Entry<LocalDate, List<venta>> entradaDia : entradaMes.getValue().entrySet()) {
                BigDecimal totalDiario = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                for (venta venta : entradaDia.getValue()) {
                    totalDiario = totalDiario.add(venta.getTotal());
                }
                lineas.add(escritorCSV.construirFila(
                        entradaMes.getKey().toString(),
                        entradaDia.getKey().toString(),
                        String.valueOf(entradaDia.getValue().size()),
                        totalDiario.toPlainString()));
            }
        }

        escritorCSV.escribirLineas(destino, lineas);
    }

    /**
     * Periodos (ano-mes) que tienen ventas registradas, del mas reciente al
     * mas antiguo. Permite al administrador elegir el mes a exportar.
     */
    public List<YearMonth> obtenerPeriodosDisponibles() {
        List<YearMonth> periodos = new ArrayList<>();
        List<venta> ventas = repoVentas.listarTodas();

        // Las ventas llegan ordenadas de forma ascendente; se recorren al reves
        // para devolver primero el periodo mas reciente.
        for (int i = ventas.size() - 1; i >= 0; i--) {
            YearMonth periodo = YearMonth.from(ventas.get(i).getFechaHora());
            if (!periodos.contains(periodo)) {
                periodos.add(periodo);
            }
        }
        return periodos;
    }

    /**
     * Un reporte sin datos no debe generar un archivo vacio: se informa el error.
     */
    private void validarDatosNoVacios(List<venta> ventas) {
        if (ventas == null || ventas.isEmpty()) {
            throw new IllegalStateException("No hay ventas registradas en el periodo seleccionado.");
        }
    }

    private String obtenerNombreCliente(venta venta) {
        cita citaAsociada = venta.getCita();
        if (citaAsociada == null || citaAsociada.getNombreCliente() == null) {
            return "Sin cliente";
        }
        return citaAsociada.getNombreCliente();
    }
}
