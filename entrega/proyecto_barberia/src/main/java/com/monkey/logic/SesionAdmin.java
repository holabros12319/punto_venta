package com.monkey.logic;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.monkey.data_persistance.RepositorioPin;

/** Sesión administrativa, validación de credencial y expiración por inactividad. */
public class SesionAdmin {

    public static final int LONGITUD_MINIMA_PIN = 6;
    public static final int LONGITUD_MAXIMA_PIN = 12;
    private static final long MAX_INACTIVITY_MINUTES = 10L;
    private static final int ITERACIONES_PBKDF2 = 210000;
    private static final int LONGITUD_SAL = 16;
    private static final int LONGITUD_HASH_BITS = 256;

    private boolean autenticado;
    private String pinConfigurado;
    private LocalDateTime ultimoAcceso;
    private final RepositorioPin repositorioPin;

    /** Constructor compatible para sesiones sin persistencia (por ejemplo, pruebas unitarias). */
    public SesionAdmin(String pinConfigurado) {
        validarFormatoPin(pinConfigurado);
        this.pinConfigurado = generarHash(pinConfigurado);
        this.repositorioPin = null;
        this.autenticado = false;
        this.ultimoAcceso = null;
    }

    /**
     * Inicializa o carga la credencial local. Un archivo heredado en texto
     * plano se valida y migra a hash; una configuración inválida no se pisa.
     */
    public SesionAdmin(String pinInicial, RepositorioPin repositorioPin) {
        validarFormatoPin(pinInicial);
        if (repositorioPin == null) {
            throw new IllegalArgumentException("El repositorio de credenciales es obligatorio.");
        }
        this.repositorioPin = repositorioPin;
        this.autenticado = false;
        this.ultimoAcceso = null;

        String guardado = repositorioPin.leerConfiguracion();
        if (guardado == null) {
            this.pinConfigurado = generarHash(pinInicial);
            repositorioPin.guardarHash(this.pinConfigurado);
        } else if (guardado.isEmpty()) {
            throw new IllegalStateException("La configuración de contraseña está vacía; no se modificó el archivo.");
        } else if (RepositorioPin.esHashConfigurado(guardado)) {
            this.pinConfigurado = guardado;
        } else if (guardado.startsWith("pbkdf2-")) {
            throw new IllegalStateException("La configuración de contraseña está dañada o usa un formato no compatible.");
        } else {
            try {
                validarFormatoPin(guardado);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("La contraseña guardada tiene un formato inválido; no se modificó el archivo.", e);
            }
            this.pinConfigurado = generarHash(guardado);
            repositorioPin.guardarHash(this.pinConfigurado);
        }
    }

    /** Valida el PIN ingresado usando PBKDF2 y comparación de tiempo constante. */
    public boolean validarPin(String pinIngresado) {
        if (pinIngresado == null) {
            return false;
        }
        char[] caracteres = pinIngresado.toCharArray();
        try {
            return validarPin(caracteres);
        } finally {
            Arrays.fill(caracteres, '\0');
        }
    }

    public boolean validarPin(char[] pinIngresado) {
        if (pinIngresado == null) {
            return false;
        }
        boolean valido = verificarHash(pinIngresado, pinConfigurado);
        if (valido) {
            this.autenticado = true;
            this.ultimoAcceso = LocalDateTime.now();
        }
        return valido;
    }

    /**
     * Cambia la credencial solo si la anterior coincide. El hash se guarda
     * primero; así un fallo de disco no deja la sesión con una clave no persistida.
     */
    public void cambiarPin(String pinAnterior, String pinNuevo) {
        char[] anterior = pinAnterior == null ? null : pinAnterior.toCharArray();
        char[] nuevo = pinNuevo == null ? null : pinNuevo.toCharArray();
        try {
            cambiarPin(anterior, nuevo);
        } finally {
            if (anterior != null) {
                Arrays.fill(anterior, '\0');
            }
            if (nuevo != null) {
                Arrays.fill(nuevo, '\0');
            }
        }
    }

    public void cambiarPin(char[] pinAnterior, char[] pinNuevo) {
        if (pinAnterior == null || !verificarHash(pinAnterior, pinConfigurado)) {
            throw new IllegalArgumentException("La contraseña anterior no es correcta.");
        }
        validarFormatoPin(pinNuevo);

        String nuevoHash = generarHash(pinNuevo);
        if (repositorioPin != null) {
            repositorioPin.guardarHash(nuevoHash);
        }
        this.pinConfigurado = nuevoHash;
        this.autenticado = true;
        this.ultimoAcceso = LocalDateTime.now();
    }

    public boolean estaAutenticado() {
        if (!autenticado || ultimoAcceso == null) {
            return false;
        }
        LocalDateTime ahora = LocalDateTime.now();
        long minutosInactivos = Duration.between(ultimoAcceso, ahora).toMinutes();
        if (minutosInactivos >= MAX_INACTIVITY_MINUTES) {
            this.autenticado = false;
            this.ultimoAcceso = null;
            return false;
        }
        this.ultimoAcceso = ahora;
        return true;
    }

    public void cerrarSesion() {
        this.autenticado = false;
        this.ultimoAcceso = null;
    }

    private static void validarFormatoPin(String pin) {
        if (pin == null) {
            throw new IllegalArgumentException("La contraseña debe tener entre " + LONGITUD_MINIMA_PIN
                    + " y " + LONGITUD_MAXIMA_PIN + " caracteres.");
        }
        char[] caracteres = pin.toCharArray();
        try {
            validarFormatoPin(caracteres);
        } finally {
            Arrays.fill(caracteres, '\0');
        }
    }

    private static void validarFormatoPin(char[] pin) {
        if (pin == null || pin.length < LONGITUD_MINIMA_PIN || pin.length > LONGITUD_MAXIMA_PIN) {
            throw new IllegalArgumentException("La contraseña debe tener entre " + LONGITUD_MINIMA_PIN
                    + " y " + LONGITUD_MAXIMA_PIN + " caracteres.");
        }
        for (int i = 0; i < pin.length; i++) {
            char caracter = pin[i];
            boolean alfanumerico = (caracter >= '0' && caracter <= '9')
                    || (caracter >= 'a' && caracter <= 'z')
                    || (caracter >= 'A' && caracter <= 'Z');
            if (!alfanumerico) {
                throw new IllegalArgumentException("La contraseña debe contener solo caracteres alfanuméricos.");
            }
        }
    }

    private static String generarHash(String pin) {
        char[] caracteres = pin.toCharArray();
        try {
            return generarHash(caracteres);
        } finally {
            Arrays.fill(caracteres, '\0');
        }
    }

    private static String generarHash(char[] pin) {
        byte[] sal = new byte[LONGITUD_SAL];
        new SecureRandom().nextBytes(sal);
        byte[] derivado = derivar(pin, sal, ITERACIONES_PBKDF2);
        return "pbkdf2-sha256$" + ITERACIONES_PBKDF2 + "$"
                + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(derivado);
    }

    private static boolean verificarHash(char[] pin, String hashGuardado) {
        if (!RepositorioPin.esHashConfigurado(hashGuardado)) {
            return false;
        }
        String[] partes = hashGuardado.split("\\$", -1);
        int iteraciones = Integer.parseInt(partes[1]);
        byte[] sal = Base64.getDecoder().decode(partes[2]);
        byte[] derivadoEsperado = Base64.getDecoder().decode(partes[3]);
        byte[] derivadoIngresado = derivar(pin, sal, iteraciones);
        return MessageDigest.isEqual(derivadoEsperado, derivadoIngresado);
    }

    private static byte[] derivar(char[] pin, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(pin, sal, iteraciones, LONGITUD_HASH_BITS);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return fabrica.generateSecret(especificacion).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el hash de la contraseña.", e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
