package com.monkey.logic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.monkey.data_persistance.RepositorioCitas;
import com.monkey.model.cita;
import com.monkey.model.servicios;

/**
 * Capa 2 - Logica de negocio de la agenda de citas.
 *
 * Responsable de la jornada laboral, del calculo de horarios disponibles
 * (RF 8), del registro de citas (RF 9, RF 10), del ciclo de estados previo al
 * cobro (RF 11, RF 12) y de la busqueda por cliente (RF 13).
 */
public class GestorAgenda {

    private static final LocalTime HORA_APERTURA = LocalTime.of(9, 0);
    private static final LocalTime HORA_CIERRE = LocalTime.of(19, 0);
    private static final String ESTADO_PENDIENTE = "Pendiente";
    private static final String ESTADO_CONFIRMADA = "Confirmada";
    private static final String ESTADO_CANCELADA = "Cancelada";
    private static final String ESTADO_COMPLETADA = "Completada";

    private final RepositorioCitas repoCitas;
    private final List<LocalTime> horariosBase;
    private final List<String> estadosSelectables;

    public GestorAgenda(RepositorioCitas repoCitas) {
        if (repoCitas == null) {
            throw new IllegalArgumentException("El repositorio de citas es obligatorio.");
        }
        this.repoCitas = repoCitas;

        // Bloques operativos fijos: de 09:00 a 20:00 en intervalos de una hora.
        this.horariosBase = new ArrayList<>();
        LocalTime bloque = HORA_APERTURA;
        while (!bloque.isAfter(HORA_CIERRE)) {
            this.horariosBase.add(bloque);
            bloque = bloque.plusHours(1);
        }

        this.estadosSelectables = new ArrayList<>();
        this.estadosSelectables.add(ESTADO_PENDIENTE);
        this.estadosSelectables.add(ESTADO_CONFIRMADA);
        this.estadosSelectables.add(ESTADO_CANCELADA);
    }

    /**
     * Registra una cita validando sus datos y delegando la persistencia.
     * El indice unico parcial de SQLite impide empalmes de horario (RF 10, RNF 5).
     */
    public void registrarCita(cita cita) {
        validarDatosCita(cita);
        repoCitas.insertar(cita);
    }

    /**
     * Horarios libres de una fecha: bloques base menos los ya ocupados.
     * Si la fecha es el dia en curso se descartan las horas ya pasadas (RF 8).
     */
    public List<LocalTime> obtenerHorariosDisponibles(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("Debe indicar una fecha para consultar la disponibilidad.");
        }

        List<LocalTime> ocupados = repoCitas.obtenerHorariosOcupados(fecha);
        LocalTime ahora = LocalTime.now();
        boolean esHoy = fecha.equals(LocalDate.now());

        List<LocalTime> disponibles = new ArrayList<>();
        for (LocalTime bloque : horariosBase) {
            if (ocupados.contains(bloque)) {
                continue;
            }
            if (esHoy && bloque.isBefore(ahora)) {
                continue;
            }
            disponibles.add(bloque);
        }
        return disponibles;
    }

    /**
     * Busca citas por coincidencia parcial del nombre del cliente (RF 13).
     * El filtro de fecha es opcional: null devuelve todas las coincidencias.
     */
    public List<cita> buscarCitas(String cliente, LocalDate fecha) {
        return repoCitas.buscarPorFiltro(cliente, fecha);
    }

    /**
     * Cambia el estado de una cita no cobrada entre Pendiente, Confirmada y
     * Cancelada. Cancelar libera automaticamente el bloque horario (RF 12).
     * "Completada" solo puede asignarse al cobrar en el punto de venta (RF 11).
     */
    public void cambiarEstadoCita(int idCita, String nuevoEstado) {
        if (nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe indicar el nuevo estado de la cita.");
        }

        String estadoSolicitado = nuevoEstado.trim();
        if (!estadosSelectables.contains(estadoSolicitado)) {
            throw new IllegalArgumentException("El estado \"" + estadoSolicitado
                    + "\" no es valido. Solo se admiten: Pendiente, Confirmada y Cancelada.");
        }

        cita actual = repoCitas.obtenerPorId(idCita);
        if (actual == null) {
            throw new IllegalArgumentException("No se encontro la cita con ID: " + idCita);
        }

        String estadoActual = actual.getEstado();
        if (ESTADO_COMPLETADA.equals(estadoActual) || ESTADO_CANCELADA.equals(estadoActual)) {
            throw new IllegalStateException("La cita se encuentra en estado \"" + estadoActual
                    + "\" y no admite cambios de estado.");
        }

        repoCitas.actualizarEstado(idCita, estadoSolicitado);
    }

    /**
     * Integridad de la cita: cliente con texto efectivo, al menos un servicio
     * y fecha/hora no anterior al momento actual.
     */
    private void validarDatosCita(cita cita) {
        if (cita == null) {
            throw new IllegalArgumentException("No se puede registrar una cita vacia.");
        }
        if (cita.getNombreCliente() == null || cita.getNombreCliente().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del cliente no puede estar vacio.");
        }
        if (cita.getFecha() == null || cita.getHora() == null) {
            throw new IllegalArgumentException("Debe indicar la fecha y la hora de la cita.");
        }
        if (cita.getServicios() == null || cita.getServicios().isEmpty()) {
            throw new IllegalArgumentException("La cita debe incluir al menos un servicio.");
        }
        for (servicios servicio : cita.getServicios()) {
            if (servicio == null) {
                throw new IllegalArgumentException("La cita no puede contener servicios vacios.");
            }
        }

        LocalDateTime inicio = LocalDateTime.of(cita.getFecha(), cita.getHora());
        if (inicio.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "No se pueden registrar citas en una fecha u hora anterior al momento actual.");
        }
    }
}
