package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cecsmsserve.entity.vo.SignedUserList;
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
 * @since 2024-07-15
 */
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT name,sex,age,telephone FROM user WHERE id=#{id}")
    SignedUserList selectUserList(int id);

    @Select("select * from user where roleId=#{rid}")
    List<User> selectByRid(int rid);

    @Select("select * from user where roleId=#{rid} limit #{start},#{size}")
    List<User> selectByRidByPage(
            @Param("rid") int rid,
            @Param("start") int start,
            @Param("size") int size);

    @Select("SELECT * FROM user WHERE name = #{name} OR idNum = #{idNum}")
    List<User> selectByNameOrIdNum(@Param("name") String name, @Param("idNum") String idNum);

    List<HashMap<String, Object>> getUsersSum();

    @Update("UPDATE user SET point = COALESCE(point, 0) + #{points} WHERE id = #{id}")
    int addPoints(@Param("id") Integer id, @Param("points") int points);
}
