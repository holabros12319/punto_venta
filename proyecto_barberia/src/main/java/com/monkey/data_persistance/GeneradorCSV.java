package com.monkey.data_persistance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GeneradorCSV {

    private final String separador;

    public GeneradorCSV() {
        this.separador = ",";
    }

    public void escribirLineas(File destino, List<String> lineas) {
        if (destino == null) {
            throw new IllegalArgumentException("La ruta de destino del archivo CSV no puede ser nula.");
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(destino), StandardCharsets.UTF_8))) {

            // Marca BOM UTF-8 para compatibilidad directa con Microsoft Excel en Windows
            writer.write('\uFEFF');

            if (lineas != null) {
                for (String linea : lineas) {
                    writer.write(linea != null ? linea : "");
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(
                "No se pudo guardar el archivo CSV : " + e.getMessage(), e
            );
        }
    }

    // Método de apoyo para construir filas seguras desde columnas individuales
    public String construirFila(String... columnas) {
        if (columnas == null || columnas.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columnas.length; i++) {
            if (i > 0) {
                sb.append(separador);
            }
            sb.append(escaparTexto(columnas[i]));
        }
        return sb.toString();
    }

    private String escaparTexto(String campo) {
        if (campo == null) {
            return "";
        }
        String limpio = campo;

        // Protección contra CSV Injection, neutralizando fórmulas maliciosas en Excel
        if (!limpio.isEmpty()) {
            char primero = limpio.charAt(0);
            if (primero == '=' || primero == '+' || primero == '-' || primero == '@') {
                limpio = "'" + limpio;
            }
        }

        // Escape estándar RFC-4180 si contiene separador, comillas o saltos de línea
        if (limpio.contains(separador) || limpio.contains("\"") || limpio.contains("\n") || limpio.contains("\r")) {
            limpio = "\"" + limpio.replace("\"", "\"\"") + "\"";
        }
        return limpio;
    }
}