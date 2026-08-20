package com.cecsmsserve.util;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyPasswordMigrationTests {

    @BeforeAll
    static void initializeMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                User.class);
    }

    @Test
    void hashesOnlyLegacyPasswordsAndUsesConditionalUpdate() {
        User legacy = user(1, "legacy-password");
        User bcrypt = user(2, "$2a$12$alreadyEncodedPasswordValue00000000000000000000000000");
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<User>>any()))
                .thenReturn(List.of(legacy, bcrypt));
        when(encoder.encode("legacy-password")).thenReturn("$2a$12$replacement");
        when(mapper.update(any(), org.mockito.ArgumentMatchers.<Wrapper<User>>any())).thenReturn(1);

        new LegacyPasswordMigration(mapper, encoder)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(encoder).encode("legacy-password");
        verify(encoder, never()).encode(bcrypt.getPassword());
        verify(mapper).update(any(), org.mockito.ArgumentMatchers.<Wrapper<User>>any());
    }

    private static User user(int id, String password) {
        User user = new User();
        user.setId(id);
        user.setPassword(password);
        return user;
    }
}
