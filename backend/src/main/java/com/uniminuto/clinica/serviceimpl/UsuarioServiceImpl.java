package com.uniminuto.clinica.serviceimpl;

import com.uniminuto.clinica.entity.Cliente;
import com.uniminuto.clinica.entity.Usuario;
import com.uniminuto.clinica.exception.BadRequestException;
import com.uniminuto.clinica.models.MiRespuestaRS;
import com.uniminuto.clinica.models.UsuarioRq;
import com.uniminuto.clinica.models.UsuarioRs;
import com.uniminuto.clinica.repository.ClienteRepository;
import com.uniminuto.clinica.repository.UsuarioRepository;
import com.uniminuto.clinica.service.UsuarioService;
import com.uniminuto.clinica.util.PasswordUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional
public class UsuarioServiceImpl implements UsuarioService {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final JdbcTemplate jdbcTemplate;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, ClienteRepository clienteRepository, JdbcTemplate jdbcTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioRs> obtenerUsuariosOrdenados() {
        return usuarioRepository.findAllByActivoOrderByUsernameAsc(true).stream().map(usuario -> {
            UsuarioRs respuesta = new UsuarioRs();
            respuesta.setId(usuario.getId());
            respuesta.setUsername(usuario.getUsername());
            respuesta.setRol(usuario.getRol());
            respuesta.setFechaCreacion(usuario.getFechaCreacion());
            respuesta.setActivo(usuario.getActivo());
            respuesta.setEmail(usuario.getEmail());
            respuesta.setClienteId(usuario.getClienteId());
            return respuesta;
        }).toList();
    }

    @Override
    public MiRespuestaRS crearUsuario(UsuarioRq solicitud) {
        validarYNormalizar(solicitud, true);
        if (usuarioRepository.existsByUsername(solicitud.getUsername())) {
            throw new BadRequestException("El nombre de usuario ya existe");
        }
        if (usuarioRepository.existsByEmail(solicitud.getEmail())) {
            throw new BadRequestException("El correo electrónico ya existe");
        }
        validarVinculoCliente(solicitud, null);

        Usuario usuario = new Usuario();
        usuario.setUsername(solicitud.getUsername());
        usuario.setPasswordHash(PasswordUtil.hash(solicitud.getPassword()));
        usuario.setRol(solicitud.getRol());
        usuario.setPerfilId(perfilId(solicitud.getRol()));
        usuario.setClienteId(solicitud.getClienteId());
        usuario.setEmail(solicitud.getEmail());
        usuario.setActivo(true);
        usuario.setFechaCreacion(LocalDateTime.now());
        usuarioRepository.save(usuario);
        return respuesta("Cuenta creada correctamente");
    }

    @Override
    public MiRespuestaRS actualizarUsuario(UsuarioRq solicitud) {
        validarYNormalizar(solicitud, false);
        if (solicitud.getId() == null) throw new BadRequestException("El identificador del usuario es obligatorio");
        Usuario usuario = usuarioRepository.findById(solicitud.getId())
                .orElseThrow(() -> new BadRequestException("El usuario no existe"));

        if (usuarioRepository.existsByUsernameAndIdNot(solicitud.getUsername(), usuario.getId())) {
            throw new BadRequestException("El nombre de usuario ya existe");
        }
        if (usuarioRepository.existsByEmailAndIdNot(solicitud.getEmail(), usuario.getId())) {
            throw new BadRequestException("El correo electrónico ya existe");
        }
        validarVinculoCliente(solicitud, usuario.getId());

        usuario.setUsername(solicitud.getUsername());
        usuario.setEmail(solicitud.getEmail());
        usuario.setRol(solicitud.getRol());
        usuario.setPerfilId(perfilId(solicitud.getRol()));
        usuario.setClienteId(solicitud.getClienteId());
        if (solicitud.getPassword() != null && !solicitud.getPassword().isBlank()) {
            usuario.setPasswordHash(PasswordUtil.hash(solicitud.getPassword()));
        }
        if (solicitud.getActivo() != null) usuario.setActivo(solicitud.getActivo());
        usuarioRepository.save(usuario);
        return respuesta("Usuario actualizado correctamente");
    }

    private void validarYNormalizar(UsuarioRq solicitud, boolean requierePassword) {
        if (solicitud == null) throw new BadRequestException("El usuario no puede estar vacío");
        String username = solicitud.getUsername() == null ? "" : solicitud.getUsername().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new BadRequestException("El usuario debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo");
        }
        solicitud.setUsername(username);

        String email = solicitud.getEmail() == null ? "" : solicitud.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new BadRequestException("Ingrese un correo electrónico válido");
        }
        solicitud.setEmail(email);
        solicitud.setRol(normalizarRol(solicitud.getRol()));

        String password = solicitud.getPassword();
        if (requierePassword && (password == null || password.length() < 10)) {
            throw new BadRequestException("La contraseña inicial debe tener al menos 10 caracteres");
        }
        if (!requierePassword && password != null && !password.isBlank() && password.length() < 10) {
            throw new BadRequestException("La nueva contraseña debe tener al menos 10 caracteres");
        }
    }

    private String normalizarRol(String rol) {
        String value = rol == null ? "" : rol.trim().toUpperCase(Locale.ROOT)
                .replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
                .replace(' ', '_').replace('-', '_');
        return switch (value) {
            case "ADMIN", "ADMINISTRADOR" -> "ADMINISTRADOR";
            case "RECEPCION", "SECRETARIA" -> "SECRETARIA";
            case "MEDICO", "VETERINARIO" -> "MEDICO";
            case "DESPACHO_MEDICO" -> "DESPACHO_MEDICO";
            case "CLIENTE" -> "CLIENTE";
            default -> throw new BadRequestException("Seleccione un perfil válido");
        };
    }

    private void validarVinculoCliente(UsuarioRq solicitud, Long usuarioActualId) {
        Long clienteId = solicitud.getClienteId();
        if (!"CLIENTE".equals(solicitud.getRol())) {
            if (clienteId != null) throw new BadRequestException("Solo una cuenta con perfil Cliente puede vincularse a una ficha de cliente");
            return;
        }
        if (clienteId == null) throw new BadRequestException("Seleccione la ficha del cliente que usará esta cuenta");
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new BadRequestException("La ficha de cliente seleccionada no existe"));
        if (Boolean.FALSE.equals(cliente.getActivo())) throw new BadRequestException("La ficha de cliente está inactiva");
        Optional<Usuario> cuentaExistente = usuarioRepository.findByClienteId(clienteId);
        if (cuentaExistente.isPresent() && !cuentaExistente.get().getId().equals(usuarioActualId)) {
            throw new BadRequestException("Esta ficha de cliente ya tiene una cuenta asociada");
        }
    }

    private Integer perfilId(String rol) {
        String nombrePerfil = switch (rol) {
            case "ADMINISTRADOR" -> "Administrador";
            case "SECRETARIA" -> "Recepcion";
            case "MEDICO" -> "Medico";
            case "DESPACHO_MEDICO" -> "Despacho medico";
            case "CLIENTE" -> "Cliente";
            default -> throw new BadRequestException("Seleccione un perfil válido");
        };
        List<Integer> ids = jdbcTemplate.query(
                "SELECT perfil_id FROM perfil WHERE LOWER(nombre)=LOWER(?) ORDER BY perfil_id LIMIT 1",
                (rs, rowNum) -> rs.getInt("perfil_id"), nombrePerfil);
        if (ids.isEmpty()) {
            throw new BadRequestException("No existe el perfil '" + nombrePerfil + "' en la base. Aplique las migraciones de la aplicación.");
        }
        return ids.get(0);
    }

    private MiRespuestaRS respuesta(String mensaje) {
        MiRespuestaRS respuesta = new MiRespuestaRS();
        respuesta.setMessage(mensaje);
        respuesta.setStatus(200);
        return respuesta;
    }
}
