package es.dam.di.infrastructure;

import es.dam.di.model.Playlist;
import es.dam.di.model.PlaylistCancion;
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

/**
 * Persiste las playlists en dos ficheros:
 * - playlists.csv: id;nombre;descripcion;usuarioId (descripcion vacía = null)
 * - playlist_canciones.csv: playlistId;posicion;cancionId (una línea por canción de cada playlist)
 */
public class PlaylistRepositorioCsv implements IPlaylistRepositorio {

    private static final Path FICHERO_PLAYLISTS = Path.of("data", "playlists.csv");
    private static final Path FICHERO_CANCIONES = Path.of("data", "playlist_canciones.csv");
    private static final String SEPARADOR = ";";
    private static final String CABECERA_PLAYLISTS = "id;nombre;descripcion;usuarioId";
    private static final String CABECERA_CANCIONES = "playlistId;posicion;cancionId";

    private final List<Playlist> playlists = new ArrayList<>();

    public PlaylistRepositorioCsv() {
        cargar();
    }

    // ---------- IRepositorio ----------

    @Override
    public long count() {
        return playlists.size();
    }

    /**
     * Al borrar la playlist desaparecen también sus líneas de playlist_canciones.csv.
     */
    @Override
    public void deleteById(Long id) {
        comprobarId(id);
        if (playlists.removeIf(p -> p.getId().equals(id))) {
            guardar();
        }
    }

    @Override
    public void deleteAll() {
        playlists.clear();
        guardar();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id) != null;
    }

    @Override
    public Playlist findById(Long id) {
        comprobarId(id);
        int posicion = buscarPosicion(id);
        if (posicion == -1) {
            return null;
        }
        return copiar(playlists.get(posicion));
    }

    @Override
    public Iterable<Playlist> findAll() {
        return findAllToList();
    }

    /**
     * Sin id inserta; con id actualiza (incluida la lista de canciones).
     */
    @Override
    public <S extends Playlist> S save(S playlist) {
        if (playlist == null) {
            throw new IllegalArgumentException("La playlist no puede ser nula");
        }
        comprobarTexto(playlist.getNombre());
        comprobarTexto(playlist.getDescripcion());

        if (playlist.getId() == null) {
            playlist.setId(siguienteId());
            playlists.add(copiar(playlist));
        } else {
            int posicion = buscarPosicion(playlist.getId());
            if (posicion == -1) {
                throw new IllegalArgumentException("No existe la playlist con id " + playlist.getId());
            }
            playlists.set(posicion, copiar(playlist));
        }
        guardar();
        return playlist;
    }

    // ---------- IRepositorioExtend ----------

    @Override
    public Optional<Playlist> findByIdOptional(Long id) {
        return Optional.ofNullable(findById(id));
    }

    @Override
    public List<Playlist> findAllToList() {
        List<Playlist> resultado = new ArrayList<>();
        for (Playlist p : playlists) {
            resultado.add(copiar(p));
        }
        return resultado;
    }

    // ---------- Métodos propios (IPlaylistRepositorio) ----------

    @Override
    public List<Playlist> findByUsuarioId(Long usuarioId) {
        List<Playlist> resultado = new ArrayList<>();
        for (Playlist p : playlists) {
            if (p.getUsuarioId().equals(usuarioId)) {
                resultado.add(copiar(p));
            }
        }
        return resultado;
    }

    @Override
    public List<Playlist> findByNombreContaining(String fragmento) {
        if (fragmento == null) {
            throw new IllegalArgumentException("El fragmento no puede ser nulo");
        }
        List<Playlist> resultado = new ArrayList<>();
        for (Playlist p : playlists) {
            if (p.getNombre().toLowerCase().contains(fragmento.toLowerCase())) {
                resultado.add(copiar(p));
            }
        }
        return resultado;
    }

    // ---------- Fichero ----------

    private void cargar() {
        cargarPlaylists();
        cargarCancionesDePlaylists();
    }

    private void cargarPlaylists() {
        if (!Files.exists(FICHERO_PLAYLISTS)) {
            return;
        }
        try (BufferedReader lector = Files.newBufferedReader(FICHERO_PLAYLISTS, StandardCharsets.UTF_8)) {
            lector.readLine(); // cabecera
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(SEPARADOR, -1);
                String descripcion = null;
                if (!campos[2].isEmpty()) {
                    descripcion = campos[2];
                }
                playlists.add(new Playlist(Long.parseLong(campos[0]), campos[1], descripcion, Long.parseLong(campos[3])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error al leer " + FICHERO_PLAYLISTS, e);
        }
    }

    private void cargarCancionesDePlaylists() {
        if (!Files.exists(FICHERO_CANCIONES)) {
            return;
        }
        try (BufferedReader lector = Files.newBufferedReader(FICHERO_CANCIONES, StandardCharsets.UTF_8)) {
            lector.readLine(); // cabecera
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(SEPARADOR, -1);
                int posicionPlaylist = buscarPosicion(Long.parseLong(campos[0]));
                if (posicionPlaylist != -1) {
                    playlists.get(posicionPlaylist).getCanciones().add(new PlaylistCancion(Integer.parseInt(campos[1]), Long.parseLong(campos[2])));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error al leer " + FICHERO_CANCIONES, e);
        }
    }

    private void guardar() {
        try {
            Files.createDirectories(FICHERO_PLAYLISTS.getParent());
            try (BufferedWriter escritor = Files.newBufferedWriter(FICHERO_PLAYLISTS, StandardCharsets.UTF_8)) {
                escritor.write(CABECERA_PLAYLISTS);
                escritor.newLine();
                for (Playlist p : playlists) {
                    String descripcion = "";
                    if (p.getDescripcion() != null) {
                        descripcion = p.getDescripcion();
                    }
                    escritor.write(p.getId() + SEPARADOR + p.getNombre() + SEPARADOR + descripcion + SEPARADOR + p.getUsuarioId());
                    escritor.newLine();
                }
            }
            try (BufferedWriter escritor = Files.newBufferedWriter(FICHERO_CANCIONES, StandardCharsets.UTF_8)) {
                escritor.write(CABECERA_CANCIONES);
                escritor.newLine();
                for (Playlist p : playlists) {
                    for (PlaylistCancion pc : p.getCanciones()) {
                        escritor.write(p.getId() + SEPARADOR + pc.getPosicion() + SEPARADOR + pc.getCancionId());
                        escritor.newLine();
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error al escribir los ficheros de playlists", e);
        }
    }

    // ---------- Auxiliares ----------

    private long siguienteId() {
        long maximo = 0;
        for (Playlist p : playlists) {
            if (p.getId() > maximo) {
                maximo = p.getId();
            }
        }
        return maximo + 1;
    }

    private int buscarPosicion(Long id) {
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Copia de la playlist (y de sus canciones): lo que sale del repositorio no es el objeto guardado.
     */
    private Playlist copiar(Playlist original) {
        Playlist copia = new Playlist(original.getId(), original.getNombre(), original.getDescripcion(), original.getUsuarioId());
        List<PlaylistCancion> canciones = new ArrayList<>();
        for (PlaylistCancion pc : original.getCanciones()) {
            canciones.add(new PlaylistCancion(pc.getId(), pc.getPosicion(), pc.getCancionId()));
        }
        copia.setCanciones(canciones);
        return copia;
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