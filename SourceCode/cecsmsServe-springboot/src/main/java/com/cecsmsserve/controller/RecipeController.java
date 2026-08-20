package com.cecsmsserve.controller;

import com.cecsmsserve.entity.Recipe;
import com.cecsmsserve.service.IRecipeService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipe")
public class RecipeController {

    private final IRecipeService recipeService;

    public RecipeController(IRecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/list")
    public CommonResult<?> getRecipeList(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "category", required = false) String category) {
        return recipeService.getRecipeList(current, size, name, category);
    }

    @GetMapping("/all")
    public CommonResult<?> getAllRecipes() {
        return recipeService.getAllRecipes();
    }

    @GetMapping("/{id}")
    public CommonResult<?> getRecipeDetail(@PathVariable Integer id) {
        if (id == null || id <= 0) {
            return CommonResult.validateFailed("菜谱编号无效");
        }
        return recipeService.getRecipeDetail(id);
    }

    @PostMapping
    public CommonResult<?> saveRecipe(@RequestBody Recipe recipe, HttpServletRequest request) {
        CommonResult<?> permissionError = requireRecipeManager(request);
        if (permissionError != null) {
            return permissionError;
        }
        String validationError = validateRecipe(recipe, false);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }

        recipe.setId(null);
        recipe.setCreateUserId(currentUserId(request));
        recipe.setStatus(1);
        return recipeService.saveRecipe(recipe);
    }

    @PutMapping
    public CommonResult<?> updateRecipe(@RequestBody Recipe recipe, HttpServletRequest request) {
        CommonResult<?> permissionError = requireRecipeManager(request);
        if (permissionError != null) {
            return permissionError;
        }
        String validationError = validateRecipe(recipe, true);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }

        // Creation ownership and logical-delete state are never accepted from the browser.
        recipe.setCreateUserId(null);
        recipe.setStatus(null);
        return recipeService.updateRecipe(recipe);
    }

    @DeleteMapping("/{id}")
    public CommonResult<?> deleteRecipe(@PathVariable Integer id, HttpServletRequest request) {
        CommonResult<?> permissionError = requireRecipeManager(request);
        if (permissionError != null) {
            return permissionError;
        }
        if (id == null || id <= 0) {
            return CommonResult.validateFailed("菜谱编号无效");
        }
        return recipeService.deleteRecipe(id);
    }

    @GetMapping("/category/{category}")
    public CommonResult<?> getRecipesByCategory(@PathVariable String category) {
        return recipeService.getRecipesByCategory(category);
    }

    @GetMapping("/search")
    public CommonResult<?> searchRecipes(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return recipeService.searchRecipes(keyword, current, size);
    }

    private CommonResult<?> requireRecipeManager(HttpServletRequest request) {
        Object roleValue = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        if (!(roleValue instanceof Integer roleId) || (roleId != 1 && roleId != 3)) {
            return CommonResult.forbidden("仅管理员或医护人员可管理菜谱");
        }
        return null;
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        if (value instanceof Integer userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated user is missing from the request");
    }

    private String validateRecipe(Recipe recipe, boolean requireId) {
        if (recipe == null) {
            return "菜谱信息不能为空";
        }
        if (requireId && (recipe.getId() == null || recipe.getId() <= 0)) {
            return "菜谱编号无效";
        }
        if (recipe.getName() == null || recipe.getName().isBlank() || recipe.getName().length() > 100) {
            return "菜谱名称需为1-100个字符";
        }
        if (recipe.getImageUrl() != null && recipe.getImageUrl().length() > 500) {
            return "菜谱图片地址不能超过500个字符";
        }
        if (recipe.getSuitableCrowd() != null && recipe.getSuitableCrowd().length() > 100) {
            return "适宜人群不能超过100个字符";
        }
        return null;
    }
}
