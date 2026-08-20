package com.cecsmsserve.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.entity.Recipe;
import com.cecsmsserve.util.result.CommonResult;

public interface IRecipeService extends IService<Recipe> {
    // 获取菜谱列表（分页+搜索+分类）
    CommonResult getRecipeList(Integer current, Integer size, String name, String category);

    // 获取所有菜谱（不分页）
    CommonResult getAllRecipes();

    // 获取菜谱详情
    CommonResult getRecipeDetail(Integer id);

    // 新增菜谱
    CommonResult saveRecipe(Recipe recipe);

    // 更新菜谱
    CommonResult updateRecipe(Recipe recipe);

    // 删除菜谱
    CommonResult deleteRecipe(Integer id);

    // 根据分类获取菜谱
    CommonResult getRecipesByCategory(String category);

    // 搜索菜谱
    CommonResult searchRecipes(String keyword, Integer current, Integer size);
}
