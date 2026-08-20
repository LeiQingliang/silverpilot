package com.cecsmsserve.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/** One-way, concurrency-safe migration for historical plaintext demo passwords. */
@Component
@ConditionalOnProperty(
        name = "app.security.migrate-legacy-passwords",
        havingValue = "true",
        matchIfMissing = true)
public class LegacyPasswordMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyPasswordMigration.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public LegacyPasswordMigration(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getPassword)
                .isNotNull(User::getPassword));
        int migrated = 0;
        for (User user : users) {
            String legacy = user.getPassword();
            if (legacy == null || legacy.matches("^\\$2[aby]\\$\\d{2}\\$.*")) continue;
            String encoded = passwordEncoder.encode(legacy);
            int changed = userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .eq(User::getPassword, legacy)
                    .set(User::getPassword, encoded));
            migrated += changed;
        }
        if (migrated > 0) {
            log.info("Migrated {} legacy password records to BCrypt", migrated);
        }
    }
}
