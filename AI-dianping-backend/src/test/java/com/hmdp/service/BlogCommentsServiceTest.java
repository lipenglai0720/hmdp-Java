package com.hmdp.service;

import com.hmdp.dto.BlogCommentRequest;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.impl.BlogCommentsServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlogCommentsServiceTest {
    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void requiresLoginAndValidCommentBeforeDatabaseAccess() {
        IBlogService blogs = mock(IBlogService.class);
        IUserService users = mock(IUserService.class);
        BlogCommentsServiceImpl service = new BlogCommentsServiceImpl(blogs, users);
        BlogCommentRequest request = new BlogCommentRequest();
        request.setBlogId(1L);
        request.setContent("  ");
        assertFalse(service.addComment(request).getSuccess());
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);
        assertFalse(service.addComment(request).getSuccess());
        request.setContent("x".repeat(256));
        assertFalse(service.addComment(request).getSuccess());
        verifyNoInteractions(blogs, users);
    }
}
