package kz.vivamind.auth;

import jakarta.validation.Valid;
import kz.vivamind.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Администратор создаёт аккаунты преподавателей. Доступ ограничен ролью ADMIN в SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/teachers")
    public List<UserDto> teachers() {
        return users.findByRoleOrderByFullName(Role.TEACHER).stream().map(UserDto::of).toList();
    }

    @PostMapping("/teachers")
    public UserDto createTeacher(@Valid @RequestBody AuthController.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Пользователь с таким email уже есть");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.TEACHER));
        return UserDto.of(user);
    }
}
