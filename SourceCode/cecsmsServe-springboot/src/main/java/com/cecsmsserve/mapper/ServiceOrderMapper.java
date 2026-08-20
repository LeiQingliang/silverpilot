package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.ServiceOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.HashMap;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-17
 */
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {

    @ResultMap("serviceOrderMap")
    @Select("select service_order.*, coalesce(mId,dId) as handlerId from service_order where uId=#{uId} order by orderDate DESC,orderTime DESC")
    List<ServiceOrder> selectByUId(int uId);

    @ResultMap("serviceOrderMap")
    @Select("select service_order.*, coalesce(mId,dId) as handlerId from service_order where uId=#{uId} and orderState=#{state}")
    List<ServiceOrder> selectByuIdByState(@Param("uId") int uId, @Param("state") int state);

    @Select("select * from service_order where orderState=#{orderState}")
    List<ServiceOrder> selectByState(int orderState);

    @ResultMap("serviceOrderMap")
    @Select("select service_order.*, coalesce(mId,dId) as handlerId from service_order order by id desc limit #{start},#{size}")
    List<ServiceOrder> selectAllByPage(@Param("start") int start, @Param("size") int size);

    @ResultMap("serviceOrderMap")
    @Select("select service_order.*, coalesce(mId,dId) as handlerId from service_order where typeBId!=4 order by id desc limit #{start},#{size}")
    List<ServiceOrder> selectServiceByPage(@Param("start") int start, @Param("size") int size);

    @ResultMap("serviceOrderMap")
    @Select("select service_order.*, coalesce(mId,dId) as handlerId from service_order where typeBId=4 order by id desc limit #{start},#{size}")
    List<ServiceOrder> selectHealthByPage(@Param("start") int start, @Param("size") int size);

    @Update("UPDATE service_order SET orderState=1 WHERE id=#{orderId} AND uId=#{userId} AND orderState=0")
    int cancelPendingByUser(@Param("orderId") Integer orderId, @Param("userId") Integer userId);

    List<HashMap<String, Object>> countRate();
}
