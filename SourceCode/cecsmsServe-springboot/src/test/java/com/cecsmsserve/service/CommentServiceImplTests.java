package com.cecsmsserve.service;

import com.cecsmsserve.entity.Comment;
import com.cecsmsserve.mapper.CommentMapper;
import com.cecsmsserve.service.impl.CommentServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceImplTests {

    @Test
    void doctorCanRestoreADeletedTopLevelComment() {
        CommentMapper mapper = mock(CommentMapper.class);
        IUserService userService = mock(IUserService.class);
        Comment deleted = new Comment();
        deleted.setId(9);
        deleted.setIsDeleted(1);
        when(mapper.selectIncludingDeleted(9)).thenReturn(deleted);
        when(mapper.restoreById(9)).thenReturn(1);

        CommonResult<Boolean> result = new CommentServiceImpl(mapper, userService)
                .restoreComment(9, 5, 3);

        assertEquals(200, result.getCode());
        assertEquals(Boolean.TRUE, result.getResult());
    }

    @Test
    void aReplyCannotBeRestoredWhileItsParentIsDeleted() {
        CommentMapper mapper = mock(CommentMapper.class);
        IUserService userService = mock(IUserService.class);
        Comment reply = new Comment();
        reply.setId(10);
        reply.setParentId(9);
        reply.setIsDeleted(1);
        Comment parent = new Comment();
        parent.setId(9);
        parent.setIsDeleted(1);
        when(mapper.selectIncludingDeleted(10)).thenReturn(reply);
        when(mapper.selectIncludingDeleted(9)).thenReturn(parent);

        CommonResult<Boolean> result = new CommentServiceImpl(mapper, userService)
                .restoreComment(10, 1, 1);

        assertEquals(400, result.getCode());
        verify(mapper, never()).restoreById(10);
    }
}
