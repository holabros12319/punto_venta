package com.monkey.data_persistance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RepositorioPinTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    @Test
    public void archivoAusenteDevuelveNulo() throws Exception {
        RepositorioPin repositorio = new RepositorioPin(carpeta.newFolder("ausente"));
        assertNull(repositorio.leerConfiguracion());
    }

    @Test
    public void guardaYLeeUnaRepresentacionDeHash() throws Exception {
        File datos = carpeta.newFolder("hash");
        RepositorioPin repositorio = new RepositorioPin(datos);
        String hash = "pbkdf2-sha256$120000$c2FsdHNhbHRzYWx0MTIzNDU2$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

        repositorio.guardarHash(hash);

        assertEquals(hash, repositorio.leerConfiguracion());
        assertEquals(hash, new String(Files.readAllBytes(new File(datos, "pin.conf").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void rechazaPersistirTextoPlano() throws Exception {
        RepositorioPin repositorio = new RepositorioPin(carpeta.newFolder("texto-plano"));
        try {
            repositorio.guardarHash("monkey2026");
            fail("El repositorio solo debe guardar hashes versionados.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("hash"));
        }
        assertNull(repositorio.leerConfiguracion());
    }

    @Test
    public void conservaValorHeredadoHastaQueSesionLoMigra() throws Exception {
        File datos = carpeta.newFolder("legado");
        Files.write(new File(datos, "pin.conf").toPath(), "monkey2026".getBytes(StandardCharsets.UTF_8));
        RepositorioPin repositorio = new RepositorioPin(datos);
        assertEquals("monkey2026", repositorio.leerConfiguracion());
        assertFalse(repositorio.leerConfiguracion().startsWith("pbkdf2-sha256$"));
    }
}
