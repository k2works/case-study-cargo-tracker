package com.example.cargotracker.authms.domain.repository;

import com.example.cargotracker.authms.domain.model.Email;
import com.example.cargotracker.authms.domain.model.User;
import com.example.cargotracker.authms.domain.model.UserId;
import com.example.cargotracker.authms.domain.model.UserName;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    void save(User user);

    void update(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(UserName username);

    Optional<User> findByEmail(Email email);

    List<User> findAll();

    boolean existsByUsername(UserName username);

    boolean existsByEmail(Email email);
}
