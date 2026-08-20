package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.ServiceType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
public interface ServiceTypeMapper extends BaseMapper<ServiceType> {

    @Select("select * from service_type where leaderId is null")
    List<ServiceType> selectAllFather();

    @Select("select * from service_type where leaderId is null and state=1")
    List<ServiceType> selectFather1();

    @Select("select * from service_type where leaderId is not null and state=1")
    List<ServiceType> selectAllChildren();

    @Select("select * from service_type where leaderId=#{id}")
    List<ServiceType> selectAllChildrenByFather(int id);

    @Select("select * from service_type where leaderId=#{id} and state=1")
    List<ServiceType> selectChildren1ByFather(int id);

}
