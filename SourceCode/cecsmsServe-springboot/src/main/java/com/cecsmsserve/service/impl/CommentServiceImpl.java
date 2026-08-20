package com.cecsmsserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.entity.Comment;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.entity.vo.CommentVo;
import com.cecsmsserve.mapper.CommentMapper;
import com.cecsmsserve.service.ICommentService;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    private final CommentMapper commentMapper;
    private final IUserService userService;

    public CommentServiceImpl(CommentMapper commentMapper, IUserService userService) {
        this.commentMapper = commentMapper;
        this.userService = userService;
    }

    @Override
    public CommonResult<List<CommentVo>> getCommentTree() {
        List<CommentVo> topComments = commentMapper.selectCommentListWithUser();
        for (CommentVo comment : topComments) {
            comment.setReplies(commentMapper.selectRepliesByParentId(comment.getId()));
        }
        return CommonResult.success(topComments);
    }

    @Override
    public CommonResult<List<CommentVo>> getManagementComments() {
        return CommonResult.success(commentMapper.selectManagementListWithUser());
    }

    @Override
    public CommonResult<CommentVo> getCommentDetail(Integer id) {
        CommentVo comment = commentMapper.selectDetailIncludingDeleted(id);
        return comment == null ? CommonResult.notFound("留言不存在") : CommonResult.success(comment);
    }

    @Override
    public CommonResult<List<CommentVo>> getReplies(Integer parentId) {
        return CommonResult.success(commentMapper.selectRepliesIncludingDeleted(parentId));
    }

    @Override
    @Transactional
    public CommonResult<Boolean> restoreComment(Integer id, Integer userId, Integer roleId) {
        if (!isForumManager(roleId)) {
            return CommonResult.forbidden("没有权限恢复留言");
        }
        Comment comment = commentMapper.selectIncludingDeleted(id);
        if (comment == null) {
            return CommonResult.notFound("留言不存在");
        }
        if (!Integer.valueOf(1).equals(comment.getIsDeleted())) {
            return CommonResult.validateFailed("留言未删除，无需恢复");
        }
        if (comment.getParentId() != null) {
            Comment parent = commentMapper.selectIncludingDeleted(comment.getParentId());
            if (parent == null || Integer.valueOf(1).equals(parent.getIsDeleted())) {
                return CommonResult.validateFailed("请先恢复父留言");
            }
        }
        return commentMapper.restoreById(id) == 1
                ? CommonResult.success(true, "恢复成功")
                : CommonResult.failed("恢复失败，请刷新后重试");
    }

    @Override
    @Transactional
    public CommonResult<Comment> addComment(Comment comment) {
        if (comment == null || comment.getUserId() == null) {
            return CommonResult.validateFailed("留言信息不完整");
        }
        User user = userService.getById(comment.getUserId());
        if (user == null) {
            return CommonResult.notFound("用户不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        comment.setId(null);
        comment.setParentId(null);
        comment.setReplyTo(null);
        comment.setReplyToUserName(null);
        comment.setUserName(user.getName());
        comment.setUserTelephone(user.getTelephone());
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        comment.setIsDeleted(0);
        return commentMapper.insert(comment) == 1
                ? CommonResult.success(comment, "留言成功")
                : CommonResult.failed("添加留言失败");
    }

    @Override
    @Transactional
    public CommonResult<Comment> replyComment(Comment comment) {
        if (comment == null || comment.getUserId() == null
                || comment.getParentId() == null || comment.getReplyTo() == null) {
            return CommonResult.validateFailed("回复信息不完整");
        }
        Comment parentComment = commentMapper.selectById(comment.getParentId());
        if (parentComment == null) {
            return CommonResult.validateFailed("回复的留言不存在或已被删除");
        }
        User user = userService.getById(comment.getUserId());
        User replyToUser = userService.getById(comment.getReplyTo());
        if (user == null || replyToUser == null) {
            return CommonResult.notFound("回复用户不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        comment.setId(null);
        comment.setUserName(user.getName());
        comment.setUserTelephone(user.getTelephone());
        comment.setReplyToUserName(replyToUser.getName());
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        comment.setIsDeleted(0);
        return commentMapper.insert(comment) == 1
                ? CommonResult.success(comment, "回复成功")
                : CommonResult.failed("回复失败");
    }

    @Override
    @Transactional
    public CommonResult<Boolean> deleteComment(
            Integer commentId, Integer currentUserId, Integer currentUserRole) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return CommonResult.notFound("留言不存在或已被删除");
        }
        boolean mayDelete = isForumManager(currentUserRole)
                || comment.getUserId() != null && comment.getUserId().equals(currentUserId);
        if (!mayDelete) {
            return CommonResult.forbidden("无权删除该留言");
        }

        LambdaUpdateWrapper<Comment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Comment::getIsDeleted, 1)
                .set(Comment::getUpdateTime, LocalDateTime.now())
                .eq(Comment::getId, commentId)
                .eq(Comment::getIsDeleted, 0);
        if (commentMapper.update(null, updateWrapper) <= 0) {
            return CommonResult.failed("删除失败，请刷新后重试");
        }

        if (comment.getParentId() == null) {
            LambdaUpdateWrapper<Comment> replies = new LambdaUpdateWrapper<>();
            replies.set(Comment::getIsDeleted, 1)
                    .set(Comment::getUpdateTime, LocalDateTime.now())
                    .eq(Comment::getParentId, commentId)
                    .eq(Comment::getIsDeleted, 0);
            commentMapper.update(null, replies);
        }
        return CommonResult.success(true, "删除成功");
    }

    @Override
    public CommonResult<List<Comment>> getMyComments(Integer userId) {
        return CommonResult.success(commentMapper.selectByUserId(userId));
    }

    private boolean isForumManager(Integer roleId) {
        return roleId != null && (roleId == 1 || roleId == 3);
    }
}
