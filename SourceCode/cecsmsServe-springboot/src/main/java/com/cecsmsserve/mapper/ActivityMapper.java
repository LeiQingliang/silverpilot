package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.Activity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
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
public interface ActivityMapper extends BaseMapper<Activity> {

    @ResultMap("activityMap")
    @Select("select * from activity")
    List<Activity> selectAll();

    @ResultMap("activityMap")
    @Select("select * from activity where state=#{state}")
    List<Activity> selectByState(int state);

    @ResultMap("activityMap")
    @Select("select * from activity where activityTypeId=#{activityTypeId}")
    List<Activity> selectByType(int activityTypeId);

    @ResultMap("activityMap")
    @Select("select * from activity order by activityDate desc limit #{start},#{size}")
    List<Activity> selectAllByPage(@Param("start") int start, @Param("size") int size);

    @ResultMap("activityMap")
    @Select("""
            SELECT * FROM activity
            WHERE state = 1
              AND (activityDate > CURRENT_DATE OR (activityDate = CURRENT_DATE AND startTime > CURRENT_TIME))
              AND (COALESCE(limitNum, 0) = 0 OR COALESCE(signNum, 0) < limitNum)
            ORDER BY activityDate ASC, startTime ASC
            LIMIT 5
            """)
    List<Activity> selectNotBegin();

    @ResultMap("activityMap")
    @Select("select * from activity where activityName like concat('%',#{name},'%')")
    List<Activity> selectByName(String name);

    @ResultMap("activityMap")
    @Select("select * from activity where activityName like concat('%',#{name},'%') order by activityDate desc limit #{start},#{size}")
    List<Activity> selectByNameByPage(
            @Param("name") String name,
            @Param("start") int start,
            @Param("size") int size);

    @ResultMap("activityMap")
    @Select("select * from activity where id=#{id}")
    Activity selectDetailById(int id);

    List<HashMap<String, Object>> countSignedUpNum();

    List<HashMap<String, Object>> countActivitySort();

    @Update("""
            UPDATE activity
            SET signNum = COALESCE(signNum, 0) + 1
            WHERE id = #{id}
              AND state = 1
              AND (activityDate > CURRENT_DATE OR (activityDate = CURRENT_DATE AND startTime > CURRENT_TIME))
              AND (COALESCE(limitNum, 0) = 0 OR COALESCE(signNum, 0) < limitNum)
            """)
    int claimAvailableSlot(int id);

    @Update("""
            UPDATE activity
            SET signNum = signNum - 1
            WHERE id = #{id} AND COALESCE(signNum, 0) > 0
            """)
    int releaseClaimedSlot(int id);

    @Update("""
            UPDATE activity
            SET state = 2
            WHERE id = #{id}
              AND state = 1
              AND TIMESTAMP(activityDate, startTime) <= CURRENT_TIMESTAMP
              AND TIMESTAMP(activityDate, endTime) > CURRENT_TIMESTAMP
            """)
    int markStartedIfDue(int id);

    @Update("""
            UPDATE activity
            SET state = 3
            WHERE id = #{id}
              AND state IN (1, 2)
              AND TIMESTAMP(activityDate, endTime) <= CURRENT_TIMESTAMP
            """)
    int markFinishedIfDue(int id);
}
