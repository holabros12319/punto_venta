package com.monkey;

import java.io.File;
import javax.swing.SwingUtilities;

import com.monkey.data_persistance.GeneradorCSV;
import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.data_persistance.RepositorioPin;
import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.data_persistance.RepositorioVentas;
import com.monkey.data_persistance.conexion_DB;
import com.monkey.logic.GeneradorReportes;
import com.monkey.logic.GestorAgenda;
import com.monkey.logic.GestorCatalogo;
import com.monkey.logic.GestorPOS;
import com.monkey.logic.SesionAdmin;
import com.monkey.ui.VentanaPrincipal;

/**
 * Punto de entrada de MONKEY ELEGANT SYSTEM.
 *
 * Rol exclusivo (regla 18 del documento de arquitectura):
 *  1. Crea la carpeta local del usuario para la base de datos.
 *  2. Abre la conexion SQLite embebida e inicializa el esquema.
 *  3. Ensambla las dependencias en orden: persistencia -> logica -> interfaz.
 *  4. Inicia la ventana principal dentro de SwingUtilities.invokeLater.
 *
 * El sistema opera 100 % local, sin servidores ni conectividad a internet.
 */
public class App {

    public static final String NOMBRE_CARPETA = "MonkeyElegantSystem";
    public static final String NOMBRE_ARCHIVO_DB = "monkey_barber.db";
    public static final String NOMBRE_ARCHIVO_PIN = RepositorioPin.NOMBRE_ARCHIVO;
    public static final String PIN_INICIAL = "monkey2026";
    public static final int LONGITUD_MINIMA_PIN = SesionAdmin.LONGITUD_MINIMA_PIN;
    public static final int LONGITUD_MAXIMA_PIN = SesionAdmin.LONGITUD_MAXIMA_PIN;

    public static void main(String[] args) {
        String carpetaDatos = resolverCarpetaDatos();
        crearCarpeta(carpetaDatos);

        conexion_DB conexion = new conexion_DB(construirUrlSqlite(carpetaDatos));
        conexion.inicializarTablas();

        RepositorioServicios repoServicios = new RepositorioServicios(conexion);
        RepositorioCitas repoCitas = new RepositorioCitas(conexion);
        RepositorioVentas repoVentas = new RepositorioVentas(conexion, repoCitas);

        GestorCatalogo gestorCatalogo = new GestorCatalogo(repoServicios);
        GestorAgenda gestorAgenda = new GestorAgenda(repoCitas);
        GestorPOS gestorPOS = new GestorPOS(repoCitas, repoVentas);
        GeneradorReportes generadorReportes = new GeneradorReportes(repoVentas, new GeneradorCSV());

        RepositorioPin repositorioPin = new RepositorioPin(new File(carpetaDatos));
        SesionAdmin sesionAdmin = new SesionAdmin(PIN_INICIAL, repositorioPin);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                VentanaPrincipal ventana = new VentanaPrincipal(gestorAgenda, gestorCatalogo, gestorPOS,
                        generadorReportes, sesionAdmin);
                ventana.iniciar();
            }
        });
    }

    /**
     * Carpeta local donde se guarda la base de datos y la configuracion.
     */
    public static String resolverCarpetaDatos() {
        return System.getProperty("user.home") + File.separator + NOMBRE_CARPETA;
    }

    /**
     * URL JDBC del archivo SQLite embebido.
     */
    public static String construirUrlSqlite(String carpetaDatos) {
        return "jdbc:sqlite:" + carpetaDatos + File.separator + NOMBRE_ARCHIVO_DB;
    }

    /**
     * Crea la carpeta local si todavia no existe.
     */
    public static void crearCarpeta(String carpetaDatos) {
        File carpeta = new File(carpetaDatos);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta local del sistema: " + carpetaDatos);
        }
    }

}
