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
        var record = toRecord(user);
        mapper.insert(record);
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
        var record = toRecord(user);
        mapper.update(record);
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
        var r = new UserRecord();
        r.id = user.id().value();
        r.username = user.username().value();
        r.email = user.email().value();
        r.password = user.passwordHash().value();
        r.enabled = user.isEnabled();
        r.createdAt = user.createdAt();
        r.updatedAt = user.updatedAt();
        return r;
    }

    private User toDomain(UserRecord r) {
        var roleNames = mapper.findRolesByUserId(r.id);
        Set<Role> roles = roleNames.stream()
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        return User.reconstruct(
                new UserId(r.id),
                new UserName(r.username),
                new Email(r.email),
                new PasswordHash(r.password),
                r.enabled,
                r.createdAt,
                r.updatedAt,
                roles
        );
    }
}
