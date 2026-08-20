package com.cecsmsserve;

import com.cecsmsserve.mapper.ActivityMapper;
import com.cecsmsserve.mapper.RecipeOrderMapper;
import com.cecsmsserve.mapper.ServiceOrderMapper;
import com.cecsmsserve.mapper.UserActivityMapper;
import com.cecsmsserve.mapper.UserMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperParameterContractTests {

    @Test
    void everyMultiParameterMapperMethodUsesExplicitNames() {
        List<Class<?>> mapperTypes = List.of(
                ActivityMapper.class,
                RecipeOrderMapper.class,
                ServiceOrderMapper.class,
                UserActivityMapper.class,
                UserMapper.class);

        for (Class<?> mapperType : mapperTypes) {
            for (Method method : mapperType.getDeclaredMethods()) {
                if (method.getParameterCount() < 2) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (com.baomidou.mybatisplus.extension.plugins.pagination.Page.class
                            .isAssignableFrom(parameter.getType())) {
                        continue;
                    }
                    assertNotNull(
                            parameter.getAnnotation(Param.class),
                            mapperType.getSimpleName() + "." + method.getName()
                                    + " must annotate every parameter with @Param");
                }
                Select select = method.getAnnotation(Select.class);
                if (select != null) {
                    assertFalse(
                            String.join(" ", select.value()).contains("#{arg"),
                            mapperType.getSimpleName() + "." + method.getName()
                                    + " must not use positional arg placeholders");
                }
            }
        }
    }

    @Test
    void xmlMappersDoNotUsePositionalArgPlaceholders() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/mapper/UserActivityMapper.xml")) {
            assertNotNull(stream);
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(xml.contains("#{arg"));
        }
    }

    @Test
    void sysFunctionMapperExplicitlyMapsTheCamelCaseParentId() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/mapper/SysFunctionMapper.xml")) {
            assertNotNull(stream);
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("column=\"fId\" property=\"parentId\""));
            assertTrue(xml.contains("resultMap=\"SysFunctionResultMap\""));
        }
    }
}
