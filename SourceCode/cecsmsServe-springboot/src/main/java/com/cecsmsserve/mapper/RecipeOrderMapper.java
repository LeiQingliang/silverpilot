package com.cecsmsserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cecsmsserve.entity.RecipeOrder;
import org.apache.ibatis.annotations.Param;

public interface RecipeOrderMapper extends BaseMapper<RecipeOrder> {
    IPage<RecipeOrder> selectUserOrders(Page<RecipeOrder> page,
                                        @Param("userId") Integer userId,
                                        @Param("recipeName") String recipeName,
                                        @Param("status") Integer status);

    IPage<RecipeOrder> selectAllOrders(Page<RecipeOrder> page,
                                       @Param("recipeName") String recipeName,
                                       @Param("status") Integer status);

    int updateOrderStatus(@Param("id") Integer id,
                          @Param("status") Integer status);
}
