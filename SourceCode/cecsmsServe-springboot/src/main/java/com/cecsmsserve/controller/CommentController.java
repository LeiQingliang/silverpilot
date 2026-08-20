package com.cecsmsserve.controller;

import com.cecsmsserve.entity.Comment;
import com.cecsmsserve.entity.vo.CommentVo;
import com.cecsmsserve.service.ICommentService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/comment")
public class CommentController {

    private final ICommentService commentService;

    public CommentController(ICommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/list")
    public CommonResult<List<CommentVo>> getCommentList() {
        return commentService.getCommentTree();
    }

    @GetMapping("/manage")
    public CommonResult<List<CommentVo>> getManagementComments(HttpServletRequest request) {
        return isForumManager(request)
                ? commentService.getManagementComments()
                : CommonResult.forbidden();
    }

    @GetMapping("/detail/{id}")
    public CommonResult<CommentVo> getCommentDetail(
            @PathVariable Integer id,
            HttpServletRequest request) {
        if (!isForumManager(request)) {
            return CommonResult.forbidden();
        }
        return id == null || id <= 0
                ? CommonResult.validateFailed("留言编号无效")
                : commentService.getCommentDetail(id);
    }

    @GetMapping("/replies/{parentId}")
    public CommonResult<List<CommentVo>> getReplies(
            @PathVariable Integer parentId,
            HttpServletRequest request) {
        if (!isForumManager(request)) {
            return CommonResult.forbidden();
        }
        return parentId == null || parentId <= 0
                ? CommonResult.validateFailed("父留言编号无效")
                : commentService.getReplies(parentId);
    }

    @PostMapping("/add")
    public CommonResult<Comment> addComment(
            @RequestBody Comment requestBody,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return CommonResult.unauthorized();
        }
        String content = normalizeContent(requestBody.getContent());
        if (content == null) {
            return CommonResult.validateFailed("留言内容不能为空且不能超过2000字");
        }

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setUserId(userId);
        return commentService.addComment(comment);
    }

    @PostMapping("/reply")
    public CommonResult<Comment> replyComment(
            @RequestBody Comment requestBody,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return CommonResult.unauthorized();
        }
        String content = normalizeContent(requestBody.getContent());
        if (content == null) {
            return CommonResult.validateFailed("回复内容不能为空且不能超过2000字");
        }
        if (requestBody.getParentId() == null || requestBody.getParentId() <= 0) {
            return CommonResult.validateFailed("回复的留言编号无效");
        }
        if (requestBody.getReplyTo() == null || requestBody.getReplyTo() <= 0) {
            return CommonResult.validateFailed("回复对象无效");
        }

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setUserId(userId);
        comment.setParentId(requestBody.getParentId());
        comment.setReplyTo(requestBody.getReplyTo());
        return commentService.replyComment(comment);
    }

    @PostMapping("/restore/{id}")
    public CommonResult<Boolean> restoreComment(
            @PathVariable Integer id,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        Integer roleId = currentRoleId(request);
        if (userId == null || roleId == null) {
            return CommonResult.unauthorized();
        }
        if (id == null || id <= 0) {
            return CommonResult.validateFailed("留言编号无效");
        }
        return commentService.restoreComment(id, userId, roleId);
    }

    @DeleteMapping("/delete/{id}")
    public CommonResult<Boolean> deleteComment(
            @PathVariable Integer id,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        Integer roleId = currentRoleId(request);
        if (userId == null || roleId == null) {
            return CommonResult.unauthorized();
        }
        if (id == null || id <= 0) {
            return CommonResult.validateFailed("留言编号无效");
        }
        return commentService.deleteComment(id, userId, roleId);
    }

    @GetMapping("/my")
    public CommonResult<List<Comment>> getMyComments(HttpServletRequest request) {
        Integer userId = currentUserId(request);
        return userId == null ? CommonResult.unauthorized() : commentService.getMyComments(userId);
    }

    private String normalizeContent(String value) {
        if (value == null) {
            return null;
        }
        String content = value.trim();
        return content.isEmpty() || content.length() > 2000 ? null : content;
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Integer id ? id : null;
    }

    private Integer currentRoleId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId ? roleId : null;
    }

    private boolean isForumManager(HttpServletRequest request) {
        Integer roleId = currentRoleId(request);
        return roleId != null && (roleId == 1 || roleId == 3);
    }
}
