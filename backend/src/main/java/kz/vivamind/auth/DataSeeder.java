package kz.vivamind.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Создаёт администратора и (по флагу) демо-аккаунты преподавателя и студента при первом запуске. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${vivamind.seed.admin-email}")
    private String adminEmail;
    @Value("${vivamind.seed.admin-password}")
    private String adminPassword;
    @Value("${vivamind.seed.demo-users}")
    private boolean demoUsers;
    @Value("${vivamind.seed.demo-password}")
    private String demoPassword;

    public DataSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(adminEmail, adminPassword, "Администратор", Role.ADMIN);
        if (demoUsers) {
            createIfMissing("teacher@vivamind.local", demoPassword, "Демо Преподаватель", Role.TEACHER);
            createIfMissing("student@vivamind.local", demoPassword, "Демо Студент", Role.STUDENT);
        }
    }

    private void createIfMissing(String email, String password, String fullName, Role role) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        users.save(new User(email.toLowerCase(), passwordEncoder.encode(password), fullName, role));
        log.info("Seeded {} account: {}", role, email);
    }
}
