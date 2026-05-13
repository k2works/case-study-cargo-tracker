package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthCommandService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthCommandService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(String username, String email, String rawPassword) {
        register(username, email, rawPassword, Role.ROLE_SHIPPER);
    }

    public void register(String username, String email, String rawPassword, Role role) {
        var userName = new UserName(username);
        var emailVO = new Email(email);

        if (userRepository.existsByUsername(userName)) {
            throw new IllegalStateException("ユーザー名 '" + username + "' は既に使用されています");
        }
        if (userRepository.existsByEmail(emailVO)) {
            throw new IllegalStateException("メールアドレス '" + email + "' は既に使用されています");
        }

        var passwordHash = new PasswordHash(passwordEncoder.encode(rawPassword));
        var user = User.create(userName, emailVO, passwordHash);
        user.addRole(role);
        userRepository.save(user);
    }
}
