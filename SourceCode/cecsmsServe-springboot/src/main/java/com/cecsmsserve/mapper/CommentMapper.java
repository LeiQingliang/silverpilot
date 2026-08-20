package com.cecsmsserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cecsmsserve.entity.Comment;
import com.cecsmsserve.entity.vo.CommentVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 查询留言列表（带用户信息）
     */
    List<CommentVo> selectCommentListWithUser();

    /**
     * 查询某个留言的回复列表
     */
    List<CommentVo> selectRepliesByParentId(@Param("parentId") Integer parentId);

    /**
     * 查询用户自己的留言
     */
    List<Comment> selectByUserId(@Param("userId") Integer userId);

    List<CommentVo> selectManagementListWithUser();

    CommentVo selectDetailIncludingDeleted(@Param("id") Integer id);

    List<CommentVo> selectRepliesIncludingDeleted(@Param("parentId") Integer parentId);

    Comment selectIncludingDeleted(@Param("id") Integer id);

    int restoreById(@Param("id") Integer id);
}
