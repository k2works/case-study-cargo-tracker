package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.UserName;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import com.example.cargotracker.authms.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthQueryService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthQueryService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public String login(String username, String rawPassword) {
        var user = userRepository.findByUsername(new UserName(username))
                .orElseThrow(() -> new IllegalArgumentException("ユーザー名またはパスワードが正しくありません"));

        if (!passwordEncoder.matches(rawPassword, user.passwordHash().value())) {
            throw new IllegalArgumentException("ユーザー名またはパスワードが正しくありません");
        }

        return jwtTokenProvider.generateToken(user.username().value());
    }
}
