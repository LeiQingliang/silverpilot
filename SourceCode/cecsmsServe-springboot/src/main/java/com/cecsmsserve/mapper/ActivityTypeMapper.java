package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.ActivityType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-17
 */
public interface ActivityTypeMapper extends BaseMapper<ActivityType> {

    @Select("select * from activity_type")
    List<ActivityType> selectAll();

    @Select("select * from activity_type where state=1")
    List<ActivityType> selectByState1();
}
