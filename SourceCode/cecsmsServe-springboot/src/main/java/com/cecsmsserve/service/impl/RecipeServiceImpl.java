package com.cecsmsserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.entity.Recipe;
import com.cecsmsserve.mapper.RecipeMapper;
import com.cecsmsserve.service.IRecipeService;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RecipeServiceImpl extends ServiceImpl<RecipeMapper, Recipe> implements IRecipeService {

    @Override
    public CommonResult getRecipeList(Integer current, Integer size, String name, String category) {
        LambdaQueryWrapper<Recipe> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Recipe::getStatus, 1);

        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like(Recipe::getName, name.trim());
        }

        if (category != null && !category.trim().isEmpty()) {
            queryWrapper.like(Recipe::getSuitableCrowd, category.trim());
        }

        queryWrapper.orderByDesc(Recipe::getCreateTime);

        Page<Recipe> page = new Page<>(normalizeCurrent(current), normalizeSize(size));
        Page<Recipe> recipePage = this.page(page, queryWrapper);

        return CommonResult.success(recipePage);
    }

    @Override
    public CommonResult getAllRecipes() {
        LambdaQueryWrapper<Recipe> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Recipe::getStatus, 1);
        queryWrapper.orderByDesc(Recipe::getCreateTime);

        List<Recipe> recipeList = this.list(queryWrapper);

        if (recipeList.isEmpty()) {
            return CommonResult.success(Collections.emptyList(), "暂无菜谱数据");
        }

        return CommonResult.success(recipeList);
    }

    @Override
    public CommonResult getRecipeDetail(Integer id) {
        Recipe recipe = this.getById(id);
        if (recipe == null) {
            return CommonResult.failed("菜谱不存在");
        }
        return CommonResult.success(recipe);
    }

    @Override
    public CommonResult saveRecipe(Recipe recipe) {
        boolean result = this.save(recipe);
        if (result) {
            return CommonResult.success("新增菜谱成功");
        } else {
            return CommonResult.failed("新增菜谱失败");
        }
    }

    @Override
    public CommonResult updateRecipe(Recipe recipe) {
        boolean result = this.updateById(recipe);
        if (result) {
            return CommonResult.success("更新菜谱成功");
        } else {
            return CommonResult.failed("更新菜谱失败");
        }
    }

    @Override
    public CommonResult deleteRecipe(Integer id) {
        Recipe recipe = this.getById(id);
        if (recipe == null) {
            return CommonResult.failed("菜谱不存在");
        }

        // 逻辑删除
        recipe.setStatus(0);
        boolean result = this.updateById(recipe);
        if (result) {
            return CommonResult.success("删除菜谱成功");
        } else {
            return CommonResult.failed("删除菜谱失败");
        }
    }

    @Override
    public CommonResult getRecipesByCategory(String category) {
        LambdaQueryWrapper<Recipe> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Recipe::getStatus, 1);

        if (category != null && !category.trim().isEmpty()) {
            queryWrapper.like(Recipe::getSuitableCrowd, category.trim());
        }

        queryWrapper.orderByDesc(Recipe::getCreateTime);
        List<Recipe> recipeList = this.list(queryWrapper);
        if (recipeList.isEmpty()) {
            return CommonResult.success(Collections.emptyList(), "该分类下暂无菜谱");
        }
        return CommonResult.success(recipeList);
    }

    @Override
    public CommonResult searchRecipes(String keyword, Integer current, Integer size) {
        Page<Recipe> page = new Page<>(normalizeCurrent(current), normalizeSize(size));
        LambdaQueryWrapper<Recipe> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Recipe::getStatus, 1);

        if (keyword != null && !keyword.trim().isEmpty()) {
            String normalizedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper.like(Recipe::getName, normalizedKeyword)
                    .or()
                    .like(Recipe::getDescription, normalizedKeyword)
                    .or()
                    .like(Recipe::getSuitableCrowd, normalizedKeyword));
        }

        queryWrapper.orderByDesc(Recipe::getCreateTime);
        return CommonResult.success(this.page(page, queryWrapper));
    }

    private long normalizeCurrent(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    private long normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
