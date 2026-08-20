package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.SysFunction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-18
 */
public interface SysFunctionMapper extends BaseMapper<SysFunction> {

    List<SysFunction> selectAll();

    List<SysFunction> selectByRid(int rid);
}
