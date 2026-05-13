package com.example.cargotracker.authms.application;

import com.example.cargotracker.authms.domain.model.Role;
import com.example.cargotracker.authms.domain.model.User;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import com.example.cargotracker.authms.interfaces.rest.dto.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminUserQueryService {

    private final UserRepository userRepository;

    public AdminUserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSummary> findAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    private UserSummary toSummary(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).toList();
        return new UserSummary(
                user.id().value(),
                user.username().value(),
                user.email().value(),
                user.isEnabled(),
                roles
        );
    }
}
