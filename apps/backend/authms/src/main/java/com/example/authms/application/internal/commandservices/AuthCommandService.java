package com.example.authms.application.internal.commandservices;

import com.example.authms.domain.model.aggregates.User;
import com.example.authms.domain.model.aggregates.UserRepository;
import com.example.authms.domain.model.valueobjects.Email;
import com.example.authms.domain.model.valueobjects.Password;
import com.example.authms.domain.model.valueobjects.Role;
import com.example.authms.domain.model.valueobjects.UserName;
import com.example.authms.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AuthCommandService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthCommandService(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("ユーザー名またはパスワードが正しくありません"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("アカウントが無効化されています");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword().getEncodedValue())) {
            throw new IllegalArgumentException("ユーザー名またはパスワードが正しくありません");
        }

        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .toList();

        return jwtTokenProvider.generateToken(username, roles);
    }

    public User register(String username, String email, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("ユーザー名は既に使用されています: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("メールアドレスは既に使用されています: " + email);
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(
                new UserName(username),
                new Email(email),
                Password.fromEncoded(encodedPassword)
        );
        user.addRole(role);

        return userRepository.save(user);
    }
}
