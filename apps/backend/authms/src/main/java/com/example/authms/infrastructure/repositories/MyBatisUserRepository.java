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
        UserRecord userRecord = userMapper.selectByUsername(username);
        return Optional.ofNullable(userRecord).map(this::toDomainModel);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        UserRecord userRecord = userMapper.selectByEmail(email);
        return Optional.ofNullable(userRecord).map(this::toDomainModel);
    }

    @Override
    public Optional<User> findById(Long id) {
        UserRecord userRecord = userMapper.selectById(id);
        return Optional.ofNullable(userRecord).map(this::toDomainModel);
    }

    @Override
    public User save(User user) {
        UserRecord userRecord = toRecord(user);
        userMapper.insertUser(userRecord);
        user.setId(userRecord.getId());

        for (Role role : user.getRoles()) {
            RoleRecord roleRecord = userMapper.selectRoleByName(role.name());
            if (roleRecord != null) {
                userMapper.insertUserRole(userRecord.getId(), roleRecord.getId());
            }
        }
        return user;
    }

    private User toDomainModel(UserRecord userRecord) {
        User user = new User(
                new UserName(userRecord.getUsername()),
                new Email(userRecord.getEmail()),
                Password.fromEncoded(userRecord.getPassword())
        );
        user.setId(userRecord.getId());
        user.setEnabled(userRecord.isEnabled());
        user.setCreatedAt(userRecord.getCreatedAt());

        List<RoleRecord> roleRecords = userMapper.selectRolesByUserId(userRecord.getId());
        Set<Role> roles = new HashSet<>();
        for (RoleRecord roleRecord : roleRecords) {
            roles.add(Role.valueOf(roleRecord.getName()));
        }
        user.setRoles(roles);
        return user;
    }

    private UserRecord toRecord(User user) {
        UserRecord userRecord = new UserRecord();
        userRecord.setUsername(user.getUsername().getValue());
        userRecord.setEmail(user.getEmail().getValue());
        userRecord.setPassword(user.getPassword().getEncodedValue());
        userRecord.setEnabled(user.isEnabled());
        return userRecord;
    }
}
