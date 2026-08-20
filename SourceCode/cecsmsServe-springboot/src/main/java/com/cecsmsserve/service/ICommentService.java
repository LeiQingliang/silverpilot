package com.cecsmsserve.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.entity.Comment;
import com.cecsmsserve.entity.vo.CommentVo;
import com.cecsmsserve.util.result.CommonResult;

import java.util.List;

public interface ICommentService extends IService<Comment> {

    /**
     * 获取留言列表（树形结构）
     */
    CommonResult<List<CommentVo>> getCommentTree();

    CommonResult<List<CommentVo>> getManagementComments();

    CommonResult<CommentVo> getCommentDetail(Integer id);

    CommonResult<List<CommentVo>> getReplies(Integer parentId);

    /**
     * 添加留言
     */
    CommonResult<Comment> addComment(Comment comment);

    /**
     * 回复留言
     */
    CommonResult<Comment> replyComment(Comment comment);

    /**
     * 删除留言（用户只能删自己的，管理员可以删所有）
     */
    CommonResult<Boolean> deleteComment(Integer commentId, Integer currentUserId, Integer currentUserRole);
    /**
     * 恢复留言
     */
    CommonResult<Boolean> restoreComment(Integer id, Integer userId, Integer roleId);
    /**
     * 获取我的留言
     */
    CommonResult<List<Comment>> getMyComments(Integer userId);
}
