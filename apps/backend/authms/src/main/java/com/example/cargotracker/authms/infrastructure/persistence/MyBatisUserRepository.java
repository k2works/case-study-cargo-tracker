package com.example.cargotracker.authms.infrastructure.persistence;

import com.example.cargotracker.authms.domain.model.*;
import com.example.cargotracker.authms.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper mapper;

    public MyBatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(User user) {
        var userRow = toRecord(user);
        mapper.insert(userRow);
        for (Role role : user.getRoles()) {
            mapper.insertUserRole(user.id().value(), role.name());
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        return mapper.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(UserName username) {
        return mapper.findByUsername(username.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return mapper.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public void update(User user) {
        var userRow = toRecord(user);
        mapper.update(userRow);
        mapper.deleteRolesByUserId(user.id().value());
        for (Role role : user.getRoles()) {
            mapper.insertUserRole(user.id().value(), role.name());
        }
    }

    @Override
    public List<User> findAll() {
        return mapper.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByUsername(UserName username) {
        return mapper.existsByUsername(username.value());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return mapper.existsByEmail(email.value());
    }

    private UserRecord toRecord(User user) {
        var userRow = new UserRecord();
        userRow.setId(user.id().value());
        userRow.setUsername(user.username().value());
        userRow.setEmail(user.email().value());
        userRow.setPassword(user.passwordHash().value());
        userRow.setEnabled(user.isEnabled());
        userRow.setCreatedAt(user.createdAt());
        userRow.setUpdatedAt(user.updatedAt());
        return userRow;
    }

    private User toDomain(UserRecord userRow) {
        var roleNames = mapper.findRolesByUserId(userRow.getId());
        Set<Role> roles = roleNames.stream()
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        return User.reconstruct(
                new UserId(userRow.getId()),
                new UserName(userRow.getUsername()),
                new Email(userRow.getEmail()),
                new PasswordHash(userRow.getPassword()),
                userRow.isEnabled(),
                userRow.getCreatedAt(),
                userRow.getUpdatedAt(),
                roles
        );
    }
}
