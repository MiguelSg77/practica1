package es.dam.di.infrastructure;

import es.dam.di.model.Cancion;
import es.dam.di.model.Playlist;
import es.dam.di.model.PlaylistCancion;
import es.dam.di.repository.ICancionRepositorio;
import es.dam.di.repository.IPlaylistRepositorio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CancionRepositorioCsv implements ICancionRepositorio {

    private static final Path FICHERO = Path.of("data", "canciones.csv");
    private static final String SEPARADOR = ";";
    private static final String CABECERA = "id;titulo;duracion;artistaId";

    private final IPlaylistRepositorio playlists;
    private final List<Cancion> canciones = new ArrayList<>();

    public CancionRepositorioCsv(IPlaylistRepositorio playlists) {
        this.playlists = playlists;
        cargar();
    }

    // ---------- IRepositorio ----------

    @Override
    public long count() {
        return canciones.size();
    }

    /**
     * Borra la canción y, en cascada, la quita de las playlists que la contengan.
     */
    @Override
    public void deleteById(Long id) {
        comprobarId(id);
        if (canciones.removeIf(c -> c.getId().equals(id))) {
            quitarDePlaylists(id);
            guardar();
        }
    }

    /**
     * Borra todas las canciones y, en cascada, vacía las playlists.
     */
    @Override
    public void deleteAll() {
        canciones.clear();
        for (Playlist p : playlists.findAllToList()) {
            if (!p.getCanciones().isEmpty()) {
                p.setCanciones(new ArrayList<>());
                playlists.save(p);
            }
        }
        guardar();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id) != null;
    }

    @Override
    public Cancion findById(Long id) {
        comprobarId(id);
        int posicion = buscarPosicion(id);
        if (posicion == -1) {
            return null;
        }
        return copiar(canciones.get(posicion));
    }

    @Override
    public Iterable<Cancion> findAll() {
        return findAllToList();
    }

    /**
     * Sin id inserta; con id actualiza (buscarla y volver a guardarla).
     */
    @Override
    public <S extends Cancion> S save(S cancion) {
        if (cancion == null) {
            throw new IllegalArgumentException("La canción no puede ser nula");
        }
        comprobarTexto(cancion.getTitulo());

        if (cancion.getId() == null) {
            cancion.setId(siguienteId());
            canciones.add(copiar(cancion));
        } else {
            int posicion = buscarPosicion(cancion.getId());
            if (posicion == -1) {
                throw new IllegalArgumentException("No existe la canción con id " + cancion.getId());
            }
            canciones.set(posicion, copiar(cancion));
        }
        guardar();
        return cancion;
    }

    // ---------- IRepositorioExtend ----------

    @Override
    public Optional<Cancion> findByIdOptional(Long id) {
        return Optional.ofNullable(findById(id));
    }

    @Override
    public List<Cancion> findAllToList() {
        List<Cancion> resultado = new ArrayList<>();
        for (Cancion c : canciones) {
            resultado.add(copiar(c));
        }
        return resultado;
    }

    // ---------- Métodos propios (ICancionRepositorio) ----------

    @Override
    public List<Cancion> findByArtistaId(Long artistaId) {
        List<Cancion> resultado = new ArrayList<>();
        for (Cancion c : canciones) {
            if (c.getArtistaId().equals(artistaId)) {
                resultado.add(copiar(c));
            }
        }
        return resultado;
    }

    @Override
    public List<Cancion> findByTituloContaining(String fragmento) {
        if (fragmento == null) {
            throw new IllegalArgumentException("El fragmento no puede ser nulo");
        }
        List<Cancion> resultado = new ArrayList<>();
        for (Cancion c : canciones) {
            if (c.getTitulo().toLowerCase().contains(fragmento.toLowerCase())) {
                resultado.add(copiar(c));
            }
        }
        return resultado;
    }

    // ---------- Cascada ----------

    /**
     * Quita la canción de todas las playlists que la contengan y vuelve a numerar
     * las posiciones de las que quedan, para que no queden huecos.
     */
    private void quitarDePlaylists(Long cancionId) {
        for (Playlist p : playlists.findAllToList()) {
            List<PlaylistCancion> restantes = new ArrayList<>();
            for (PlaylistCancion pc : p.getCanciones()) {
                if (!pc.getCancionId().equals(cancionId)) {
                    restantes.add(pc);
                }
            }
            if (restantes.size() != p.getCanciones().size()) {
                for (int i = 0; i < restantes.size(); i++) {
                    restantes.get(i).setPosicion(i);
                }
                p.setCanciones(restantes);
                playlists.save(p);
            }
        }
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
                canciones.add(new Cancion(Long.parseLong(campos[0]), campos[1], Integer.parseInt(campos[2]), Long.parseLong(campos[3])));
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
                for (Cancion c : canciones) {
                    escritor.write(c.getId() + SEPARADOR + c.getTitulo() + SEPARADOR + c.getDuracion() + SEPARADOR + c.getArtistaId());
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
        for (Cancion c : canciones) {
            if (c.getId() > maximo) {
                maximo = c.getId();
            }
        }
        return maximo + 1;
    }

    private int buscarPosicion(Long id) {
        for (int i = 0; i < canciones.size(); i++) {
            if (canciones.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Copia de la canción: lo que sale del repositorio no es el objeto guardado en la lista.
     */
    private Cancion copiar(Cancion original) {
        return new Cancion(original.getId(), original.getTitulo(), original.getDuracion(), original.getArtistaId());
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