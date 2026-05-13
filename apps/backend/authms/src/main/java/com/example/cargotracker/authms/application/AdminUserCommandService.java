package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.Role;
import com.example.cargotracker.authms.domain.model.UserId;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminUserCommandService {

    private final UserRepository userRepository;

    public AdminUserCommandService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void updateRoles(String userId, List<String> roleNames) {
        var user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
        Set<Role> roles = roleNames.stream()
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        user.setRoles(roles);
        userRepository.update(user);
    }

    public void updateStatus(String userId, boolean enabled) {
        var user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
        if (enabled) {
            user.enable();
        } else {
            user.disable();
        }
        userRepository.update(user);
    }
}
