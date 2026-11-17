package com.monkey.data_persistance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Capa 3 - Pruebas del escritor CSV.
 * Verifica la codificacion UTF-8 con BOM, el escape RFC 4180 y la
 * neutralizacion de celdas con formula (RNF 13).
 */
public class GeneradorCSVTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    private final GeneradorCSV escritor = new GeneradorCSV();

    @Test
    public void escribirLineasGeneraArchivoConMarcaBOM() throws IOException {
        File destino = new File(carpeta.getRoot(), "reporte.csv");
        List<String> lineas = new ArrayList<>();
        lineas.add(escritor.construirFila("ID Venta", "Cliente", "Total"));
        lineas.add(escritor.construirFila("1", "Juan Perez", "50.00"));

        escritor.escribirLineas(destino, lineas);

        assertTrue(destino.exists());
        byte[] contenido = Files.readAllBytes(destino.toPath());
        assertEquals("El archivo debe iniciar con la marca BOM para Excel.", (byte) 0xEF, contenido[0]);
        assertEquals((byte) 0xBB, contenido[1]);
        assertEquals((byte) 0xBF, contenido[2]);

        String texto = new String(contenido, StandardCharsets.UTF_8);
        assertTrue(texto.contains("ID Venta,Cliente,Total"));
        assertTrue(texto.contains("1,Juan Perez,50.00"));
    }

    @Test
    public void construirFilaEscapaComasYComillas() {
        String fila = escritor.construirFila("Corte, con coma", "Comillas \" aqui");

        assertEquals("\"Corte, con coma\",\"Comillas \"\" aqui\"", fila);
    }

    @Test
    public void construirFilaNeutralizaFormulasDeExcel() {
        assertEquals("'=SUM(A1)", escritor.construirFila("=SUM(A1)"));
        assertEquals("'+1234", escritor.construirFila("+1234"));
        assertEquals("'-15", escritor.construirFila("-15"));
        assertEquals("'@cmd", escritor.construirFila("@cmd"));
    }

    @Test
    public void construirFilaConColumnasVaciasONulas() {
        assertEquals("", escritor.construirFila());
        assertEquals(",", escritor.construirFila("", ""));
        assertEquals("Juan,,50.00", escritor.construirFila("Juan", null, "50.00"));
    }

    @Test
    public void escribirLineasRechazaDestinoNulo() {
        try {
            escritor.escribirLineas(null, new ArrayList<String>());
            org.junit.Assert.fail("Debe rechazar un destino nulo.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("destino"));
        }
    }
}
