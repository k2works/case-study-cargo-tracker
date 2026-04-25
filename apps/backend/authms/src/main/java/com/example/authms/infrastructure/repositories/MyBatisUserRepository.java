package com.example.authms.infrastructure.repositories;

import com.example.authms.domain.model.aggregates.User;
import com.example.authms.domain.model.aggregates.UserRepository;
import com.example.authms.domain.model.valueobjects.Email;
import com.example.authms.domain.model.valueobjects.Password;
import com.example.authms.domain.model.valueobjects.Role;
import com.example.authms.domain.model.valueobjects.UserName;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    public MyBatisUserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserRecord record = userMapper.selectByUsername(username);
        return Optional.ofNullable(record).map(this::toDomainModel);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        UserRecord record = userMapper.selectByEmail(email);
        return Optional.ofNullable(record).map(this::toDomainModel);
    }

    @Override
    public Optional<User> findById(Long id) {
        UserRecord record = userMapper.selectById(id);
        return Optional.ofNullable(record).map(this::toDomainModel);
    }

    @Override
    public User save(User user) {
        UserRecord record = toRecord(user);
        userMapper.insertUser(record);
        user.setId(record.getId());

        for (Role role : user.getRoles()) {
            RoleRecord roleRecord = userMapper.selectRoleByName(role.name());
            if (roleRecord != null) {
                userMapper.insertUserRole(record.getId(), roleRecord.getId());
            }
        }
        return user;
    }

    private User toDomainModel(UserRecord record) {
        User user = new User(
                new UserName(record.getUsername()),
                new Email(record.getEmail()),
                Password.fromEncoded(record.getPassword())
        );
        user.setId(record.getId());
        user.setEnabled(record.isEnabled());
        user.setCreatedAt(record.getCreatedAt());

        List<RoleRecord> roleRecords = userMapper.selectRolesByUserId(record.getId());
        Set<Role> roles = new HashSet<>();
        for (RoleRecord rr : roleRecords) {
            roles.add(Role.valueOf(rr.getName()));
        }
        user.setRoles(roles);
        return user;
    }

    private UserRecord toRecord(User user) {
        UserRecord record = new UserRecord();
        record.setUsername(user.getUsername().getValue());
        record.setEmail(user.getEmail().getValue());
        record.setPassword(user.getPassword().getEncodedValue());
        record.setEnabled(user.isEnabled());
        return record;
    }
}
