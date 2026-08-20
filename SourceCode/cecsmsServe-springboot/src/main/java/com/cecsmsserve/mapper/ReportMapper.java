package com.cecsmsserve.mapper;

import com.cecsmsserve.entity.Report;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author GoatCode
 * @since 2024-08-20
 */
public interface ReportMapper extends BaseMapper<Report> {

    @ResultMap("reportMap")
    @Select("select * from report where uId=#{uId}")
    List<Report> selectByuId(int uId);
}
