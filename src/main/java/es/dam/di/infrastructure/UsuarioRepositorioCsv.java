package es.dam.di.infrastructure;

import es.dam.di.model.Playlist;
import es.dam.di.model.Usuario;
import es.dam.di.repository.IPlaylistRepositorio;
import es.dam.di.repository.IUsuarioRepositorio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuarioRepositorioCsv implements IUsuarioRepositorio {

    private static final Path FICHERO = Path.of("data", "usuarios.csv");
    private static final String SEPARADOR = ";";
    private static final String CABECERA = "id;nombre;email";

    private final IPlaylistRepositorio playlists;
    private final List<Usuario> usuarios = new ArrayList<>();

    public UsuarioRepositorioCsv(IPlaylistRepositorio playlists) {
        this.playlists = playlists;
        cargar();
    }

    // ---------- IRepositorio ----------

    @Override
    public long count() {
        return usuarios.size();
    }

    /**
     * Borra el usuario y, en cascada, sus playlists.
     */
    @Override
    public void deleteById(Long id) {
        comprobarId(id);
        if (usuarios.removeIf(u -> u.getId().equals(id))) {
            for (Playlist p : playlists.findByUsuarioId(id)) {
                playlists.deleteById(p.getId());
            }
            guardar();
        }
    }

    /**
     * Borra todos los usuarios y, en cascada, todas las playlists.
     */
    @Override
    public void deleteAll() {
        usuarios.clear();
        playlists.deleteAll();
        guardar();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id) != null;
    }

    @Override
    public Usuario findById(Long id) {
        comprobarId(id);
        int posicion = buscarPosicion(id);
        if (posicion == -1) {
            return null;
        }
        return copiar(usuarios.get(posicion));
    }

    @Override
    public Iterable<Usuario> findAll() {
        return findAllToList();
    }

    /**
     * Sin id inserta; con id actualiza. El email no se puede repetir.
     */
    @Override
    public <S extends Usuario> S save(S usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario no puede ser nulo");
        }
        comprobarTexto(usuario.getNombre());
        comprobarTexto(usuario.getEmail());
        comprobarEmailLibre(usuario);

        if (usuario.getId() == null) {
            usuario.setId(siguienteId());
            usuarios.add(copiar(usuario));
        } else {
            int posicion = buscarPosicion(usuario.getId());
            if (posicion == -1) {
                throw new IllegalArgumentException("No existe el usuario con id " + usuario.getId());
            }
            usuarios.set(posicion, copiar(usuario));
        }
        guardar();
        return usuario;
    }

    // ---------- IRepositorioExtend ----------

    @Override
    public Optional<Usuario> findByIdOptional(Long id) {
        return Optional.ofNullable(findById(id));
    }

    @Override
    public List<Usuario> findAllToList() {
        List<Usuario> resultado = new ArrayList<>();
        for (Usuario u : usuarios) {
            resultado.add(copiar(u));
        }
        return resultado;
    }

    // ---------- Métodos propios (IUsuarioRepositorio) ----------

    /**
     * La comparación del email no distingue mayúsculas de minúsculas.
     */
    @Override
    public Optional<Usuario> findByEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("El email no puede ser nulo");
        }
        for (Usuario u : usuarios) {
            if (u.getEmail().equalsIgnoreCase(email)) {
                return Optional.of(copiar(u));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
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
                usuarios.add(new Usuario(Long.parseLong(campos[0]), campos[1], campos[2]));
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
                for (Usuario u : usuarios) {
                    escritor.write(u.getId() + SEPARADOR + u.getNombre() + SEPARADOR + u.getEmail());
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
        for (Usuario u : usuarios) {
            if (u.getId() > maximo) {
                maximo = u.getId();
            }
        }
        return maximo + 1;
    }

    private int buscarPosicion(Long id) {
        for (int i = 0; i < usuarios.size(); i++) {
            if (usuarios.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Copia del usuario: lo que sale del repositorio no es el objeto guardado en la lista.
     */
    private Usuario copiar(Usuario original) {
        return new Usuario(original.getId(), original.getNombre(), original.getEmail());
    }

    /**
     * Lanza excepción si OTRO usuario (con distinto id) ya tiene ese email.
     */
    private void comprobarEmailLibre(Usuario usuario) {
        for (Usuario u : usuarios) {
            boolean mismoEmail = u.getEmail().equalsIgnoreCase(usuario.getEmail());
            boolean otroUsuario = !u.getId().equals(usuario.getId());
            if (mismoEmail && otroUsuario) {
                throw new IllegalArgumentException("Ya existe un usuario con el email " + usuario.getEmail());
            }
        }
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