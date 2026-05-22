package com.example.authms.infrastructure.init;

import com.example.authms.domain.Role;
import com.example.authms.domain.User;
import com.example.authms.domain.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * local-h2 プロファイル専用の初期データ投入。
 * Flyway が無効な H2 環境でのみ実行し、admin ユーザーを生成する。
 */
@Component
@Profile({"local-h2", "local-docker"})
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("password"));
            admin.setRole(Role.ROLE_ADMIN);
            admin.setFailedLoginAttempts(0);
            userRepository.save(admin);
        }
    }
}
