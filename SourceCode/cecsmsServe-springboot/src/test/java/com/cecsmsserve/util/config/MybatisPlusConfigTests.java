package com.cecsmsserve.util.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTests {

    @Test
    void configuresFullTableWriteProtectionBeforeBoundedMysqlPagination() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).hasSize(2);
        assertThat(interceptor.getInterceptors().getFirst())
                .isInstanceOf(BlockAttackInnerInterceptor.class);
        assertThat(interceptor.getInterceptors().get(1))
                .isInstanceOf(PaginationInnerInterceptor.class);

        PaginationInnerInterceptor pagination =
                (PaginationInnerInterceptor) interceptor.getInterceptors().get(1);
        assertThat(pagination.getMaxLimit()).isEqualTo(MybatisPlusConfig.MAX_PAGE_SIZE);
        assertThat(pagination.isOverflow()).isFalse();
    }
}
