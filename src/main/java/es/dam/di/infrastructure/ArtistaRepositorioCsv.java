package es.dam.di.infrastructure;

import es.dam.di.model.Artista;
import es.dam.di.model.Cancion;
import es.dam.di.repository.IArtistaRepositorio;
import es.dam.di.repository.ICancionRepositorio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArtistaRepositorioCsv implements IArtistaRepositorio {

    private static final Path FICHERO = Path.of("data", "artistas.csv");
    private static final String SEPARADOR = ";";
    private static final String CABECERA = "id;nombre;pais";

    private final ICancionRepositorio canciones;
    private final List<Artista> artistas = new ArrayList<>();

    public ArtistaRepositorioCsv(ICancionRepositorio canciones) {
        this.canciones = canciones;
        cargar();
    }

    // ---------- IRepositorio ----------

    @Override
    public long count() {
        return artistas.size();
    }

    /**
     * Borra el artista y, en cascada, sus canciones.
     */
    @Override
    public void deleteById(Long id) {
        comprobarId(id);
        if (artistas.removeIf(a -> a.getId().equals(id))) {
            for (Cancion c : canciones.findByArtistaId(id)) {
                canciones.deleteById(c.getId());
            }
            guardar();
        }
    }

    /**
     * Borra todos los artistas y, en cascada, todas las canciones.
     */
    @Override
    public void deleteAll() {
        artistas.clear();
        canciones.deleteAll();
        guardar();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id) != null;
    }

    @Override
    public Artista findById(Long id) {
        comprobarId(id);
        int posicion = buscarPosicion(id);
        if (posicion == -1) {
            return null;
        }
        return copiar(artistas.get(posicion));
    }

    @Override
    public Iterable<Artista> findAll() {
        return findAllToList();
    }

    /**
     * Sin id inserta; con id actualiza (buscarlo y volver a guardarlo).
     */
    @Override
    public <S extends Artista> S save(S artista) {
        if (artista == null) {
            throw new IllegalArgumentException("El artista no puede ser nulo");
        }
        comprobarTexto(artista.getNombre());
        comprobarTexto(artista.getPais());

        if (artista.getId() == null) {
            artista.setId(siguienteId());
            artistas.add(copiar(artista));
        } else {
            int posicion = buscarPosicion(artista.getId());
            if (posicion == -1) {
                throw new IllegalArgumentException("No existe el artista con id " + artista.getId());
            }
            artistas.set(posicion, copiar(artista));
        }
        guardar();
        return artista;
    }

    // ---------- IRepositorioExtend ----------

    @Override
    public Optional<Artista> findByIdOptional(Long id) {
        return Optional.ofNullable(findById(id));
    }

    @Override
    public List<Artista> findAllToList() {
        List<Artista> resultado = new ArrayList<>();
        for (Artista a : artistas) {
            resultado.add(copiar(a));
        }
        return resultado;
    }

    // ---------- Métodos propios (IArtistaRepositorio) ----------

    @Override
    public List<Artista> findByPais(String pais) {
        List<Artista> resultado = new ArrayList<>();
        for (Artista a : artistas) {
            if (a.getPais().equals(pais)) {
                resultado.add(copiar(a));
            }
        }
        return resultado;
    }

    @Override
    public List<Artista> findByNombreContaining(String fragmento) {
        if (fragmento == null) {
            throw new IllegalArgumentException("El fragmento no puede ser nulo");
        }
        List<Artista> resultado = new ArrayList<>();
        for (Artista a : artistas) {
            if (a.getNombre().toLowerCase().contains(fragmento.toLowerCase())) {
                resultado.add(copiar(a));
            }
        }
        return resultado;
    }

    // ---------- Fichero ----------

    private void cargar() {
        if (!Files.exists(FICHERO)) {
            return;
        }
        try (BufferedReader lector = Files.newBufferedReader(FICHERO, StandardCharsets.UTF_8)) {
            lector.readLine(); // cabecera
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(SEPARADOR, -1);
                artistas.add(new Artista(Long.parseLong(campos[0]), campos[1], campos[2]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error al leer " + FICHERO, e);
        }
    }

    private void guardar() {
        try {
            Files.createDirectories(FICHERO.getParent());
            try (BufferedWriter escritor = Files.newBufferedWriter(FICHERO, StandardCharsets.UTF_8)) {
                escritor.write(CABECERA);
                escritor.newLine();
                for (Artista a : artistas) {
                    escritor.write(a.getId() + SEPARADOR + a.getNombre() + SEPARADOR + a.getPais());
                    escritor.newLine();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error al escribir " + FICHERO, e);
        }
    }

    // ---------- Auxiliares ----------

    private long siguienteId() {
        long maximo = 0;
        for (Artista a : artistas) {
            if (a.getId() > maximo) {
                maximo = a.getId();
            }
        }
        return maximo + 1;
    }

    private int buscarPosicion(Long id) {
        for (int i = 0; i < artistas.size(); i++) {
            if (artistas.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Copia del artista: lo que sale del repositorio no es el objeto guardado en la lista.
     */
    private Artista copiar(Artista original) {
        return new Artista(original.getId(), original.getNombre(), original.getPais());
    }

    private void comprobarId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El id no puede ser nulo");
        }
    }

    private void comprobarTexto(String texto) {
        if (texto == null) {
            return;
        }
        if (texto.contains(SEPARADOR) || texto.contains("\n") || texto.contains("\r")) {
            throw new IllegalArgumentException("El texto no puede contener '" + SEPARADOR + "' ni saltos de línea");
        }
    }
}