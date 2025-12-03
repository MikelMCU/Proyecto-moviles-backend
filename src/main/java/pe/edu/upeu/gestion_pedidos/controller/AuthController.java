package pe.edu.upeu.gestion_pedidos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.gestion_pedidos.dto.AuthResponseDTO;
import pe.edu.upeu.gestion_pedidos.dto.LoginRequestDTO;
import pe.edu.upeu.gestion_pedidos.dto.UserDTO;
import pe.edu.upeu.gestion_pedidos.entity.User;
import pe.edu.upeu.gestion_pedidos.enums.Role;
import pe.edu.upeu.gestion_pedidos.repository.UserRepository;
import pe.edu.upeu.gestion_pedidos.service.JwtService;
import pe.edu.upeu.gestion_pedidos.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody LoginRequestDTO request) {

        String passwordEncriptada = passwordEncoder.encode(request.getPassword());
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setPasswordHash(passwordEncriptada);
        newUser.setFullName("Usuario Nuevo");
        newUser.setRole(Role.USER);

        return ResponseEntity.ok(userRepository.save(newUser));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        String role = user.getRole() != null ? user.getRole().name() : "USER";
        String token = jwtService.generateToken(user.getId(), user.getEmail(), role);
        return ResponseEntity.ok(AuthResponseDTO.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@RequestHeader("Authorization") String token) {
        String userId = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        UserDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
    @GetMapping("/users")
    public ResponseEntity<List<User>> listAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }
}