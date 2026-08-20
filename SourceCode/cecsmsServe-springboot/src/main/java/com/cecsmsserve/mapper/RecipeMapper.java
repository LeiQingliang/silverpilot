package com.cecsmsserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cecsmsserve.entity.Recipe;
import org.apache.ibatis.annotations.Param;

public interface RecipeMapper extends BaseMapper<Recipe> {
    IPage<Recipe> selectRecipePage(Page<Recipe> page,
                                   @Param("name") String name);

    int logicalDelete(@Param("id") Integer id);
}