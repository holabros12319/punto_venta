package com.monkey.logic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.monkey.data_persistance.RepositorioServicios;
import com.monkey.model.servicios;

/**
 * Capa 2 - Logica de negocio del catalogo de servicios.
 *
 * Centraliza las reglas del catalogo: categorias cerradas (RF 3), alta (RF 4),
 * edicion (RF 5), baja logica (RF 6) y busqueda aislada (RF 7).
 */
public class GestorCatalogo {

    public static final String CATEGORIA_CORTES = "Cortes de pelo";
    public static final String CATEGORIA_OTROS = "Otros servicios";

    private final RepositorioServicios repoServicios;
    private final List<String> categoriasFijas;

    public GestorCatalogo(RepositorioServicios repoServicios) {
        if (repoServicios == null) {
            throw new IllegalArgumentException("El repositorio de servicios es obligatorio.");
        }
        this.repoServicios = repoServicios;
        this.categoriasFijas = new ArrayList<>();
        this.categoriasFijas.add(CATEGORIA_CORTES);
        this.categoriasFijas.add(CATEGORIA_OTROS);
    }

    /**
     * Categorias validas del catalogo (lista unica para la interfaz).
     */
    public List<String> obtenerCategorias() {
        return new ArrayList<>(categoriasFijas);
    }

    /**
     * Da de alta un servicio nuevo. Si ya existe un servicio con el mismo nombre
     * (activo o inactivo) se rechaza: un servicio inactivo debe reactivarse
     * editando su registro, nunca duplicandolo (RF 4).
     */
    public void guardarServicio(servicios servicio) {
        validarServicio(servicio);

        List<servicios> existentes = repoServicios.listarTodos();
        for (servicios actual : existentes) {
            if (!actual.getNombre().trim().equalsIgnoreCase(servicio.getNombre().trim())) {
                continue;
            }
            if (actual.getIdServicio() == servicio.getIdServicio()) {
                return;
            }
            if (!actual.isActivo()) {
                throw new IllegalArgumentException("El servicio \"" + actual.getNombre().trim()
                        + "\" ya existe como servicio inactivo. Reactive el registro existente en lugar de crear un duplicado.");
            }
            throw new IllegalArgumentException("Ya existe un servicio registrado con el nombre \""
                    + actual.getNombre().trim() + "\".");
        }

        repoServicios.insertar(servicio);
    }

    /**
     * Edita un servicio ya registrado (nombre, precio, categoria y estado).
     * Las modificaciones no alteran las ventas historicas ya registradas (RNF 4).
     */
    public void editarServicio(servicios servicio) {
        validarServicio(servicio);

        if (servicio.getIdServicio() <= 0) {
            throw new IllegalArgumentException("No se puede editar un servicio que todavia no ha sido registrado.");
        }

        List<servicios> existentes = repoServicios.listarTodos();
        boolean existe = false;
        for (servicios actual : existentes) {
            if (actual.getIdServicio() == servicio.getIdServicio()) {
                existe = true;
                continue;
            }
            if (actual.getNombre().trim().equalsIgnoreCase(servicio.getNombre().trim())) {
                throw new IllegalArgumentException("Ya existe otro servicio con el nombre \""
                        + actual.getNombre().trim() + "\".");
            }
        }
        if (!existe) {
            throw new IllegalArgumentException("No se encontro el servicio con ID: " + servicio.getIdServicio());
        }

        repoServicios.actualizar(servicio);
    }

    /**
     * Consulta aislada del catalogo por coincidencia parcial de nombre (RF 7).
     *
     * @param texto      fragmento a buscar; vacio o nulo devuelve todos los servicios
     * @param soloActivos true para listar unicamente los servicios activos
     */
    public List<servicios> filtrarServicios(String texto, boolean soloActivos) {
        List<servicios> base = soloActivos ? repoServicios.listarActivos() : repoServicios.listarTodos();

        String filtro = (texto == null) ? "" : texto.trim().toLowerCase();
        List<servicios> resultados = new ArrayList<>();
        for (servicios servicio : base) {
            String nombre = (servicio.getNombre() == null) ? "" : servicio.getNombre().toLowerCase();
            if (filtro.isEmpty() || nombre.contains(filtro)) {
                resultados.add(servicio);
            }
        }
        return resultados;
    }

    /**
     * Reglas de integridad del catalogo: nombre con texto efectivo, costo mayor
     * a cero y pertenencia a una de las dos categorias fijas.
     */
    private void validarServicio(servicios servicio) {
        if (servicio == null) {
            throw new IllegalArgumentException("No se puede registrar un servicio vacio.");
        }
        if (servicio.getNombre() == null || servicio.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del servicio no puede estar vacio.");
        }
        if (servicio.getCostoUnitario() == null
                || servicio.getCostoUnitario().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio unitario del servicio debe ser mayor a cero.");
        }
        if (servicio.getCategoria() == null || !categoriasFijas.contains(servicio.getCategoria())) {
            throw new IllegalArgumentException("La categoria \"" + servicio.getCategoria()
                    + "\" no es valida. Solo se admiten: " + CATEGORIA_CORTES + " y " + CATEGORIA_OTROS + ".");
        }
    }
}
