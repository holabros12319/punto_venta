package com.monkey.data_persistance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * Persiste la credencial administrativa como hash versionado en un archivo
 * local, separado de la base de datos SQLite.
 */
public class RepositorioPin {

    public static final String NOMBRE_ARCHIVO = "pin.conf";

    private final Path archivoPin;

    public RepositorioPin(File carpetaDatos) {
        if (carpetaDatos == null) {
            throw new IllegalArgumentException("La carpeta local de credenciales es obligatoria.");
        }
        this.archivoPin = carpetaDatos.toPath().resolve(NOMBRE_ARCHIVO);
    }

    /**
     * Devuelve el contenido configurado o null si aún no existe el archivo.
     * El valor heredado en texto plano se devuelve para que SesionAdmin lo
     * valide y lo migre sin sobrescribir una configuración no reconocida.
     */
    public String leerConfiguracion() {
        if (!Files.exists(archivoPin)) {
            return null;
        }
        try {
            return Files.readString(archivoPin, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la configuración de la contraseña: " + e.getMessage(), e);
        }
    }

    /** Guarda únicamente hashes PBKDF2 con formato versionado. */
    public void guardarHash(String hashVersionado) {
        if (!esHashConfigurado(hashVersionado)) {
            throw new IllegalArgumentException("El repositorio solo puede guardar un hash PBKDF2 versionado.");
        }

        Path temporal = archivoPin.resolveSibling(archivoPin.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(archivoPin.getParent());
            Files.writeString(temporal, hashVersionado, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporal, archivoPin, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivoPin, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException errorLimpieza) {
                e.addSuppressed(errorLimpieza);
            }
            throw new IllegalStateException("No se pudo guardar el hash de la contraseña: " + e.getMessage(), e);
        }
    }

    public static boolean esHashConfigurado(String valor) {
        if (valor == null) {
            return false;
        }
        String[] partes = valor.split("\\$", -1);
        if (partes.length != 4 || !"pbkdf2-sha256".equals(partes[0])) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] derivado = Base64.getDecoder().decode(partes[3]);
            return iteraciones >= 100000 && iteraciones <= 2000000 && sal.length >= 16 && derivado.length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
