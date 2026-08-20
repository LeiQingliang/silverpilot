package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.UserActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cecsmsserve.entity.vo.MyActivity;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-22
 */
public interface UserActivityMapper extends BaseMapper<UserActivity> {

    @ResultMap("userActivityMap")
    @Select("select * from user_activity")
    List<UserActivity> selectAll();

    @ResultMap("userActivityMap")
    @Select("select * from user_activity limit #{start},#{size}")
    List<UserActivity> selectAllByPage(@Param("start") int start, @Param("size") int size);

    List<MyActivity> selectAllByuId(int uId);

    List<MyActivity> selectByUIdBymyState(@Param("uId") int uId, @Param("state") String state);

    List<MyActivity> selectByUIdByState(@Param("uId") int uId, @Param("state") String state);

    @Select("select * from user_activity where uId=#{uId} and aId=#{aId}")
    UserActivity selectByuIdByaId(@Param("uId") int uId, @Param("aId") int aId);

    @Select("select uId from user_activity where aId=#{aId} and state=\"报名成功\" order by uId")
    List<Integer> selectUserByaId(int aId);

    @Update("""
            UPDATE user_activity
            SET state = '已取消报名'
            WHERE id = #{id}
              AND uId = #{userId}
              AND aId = #{activityId}
              AND state = '报名成功'
            """)
    int cancelConfirmed(
            @Param("id") Integer id,
            @Param("userId") Integer userId,
            @Param("activityId") Integer activityId);
}
