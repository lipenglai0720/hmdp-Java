package com.hmdp.controller;

import com.hmdp.dto.UserDTO;
import com.hmdp.dto.ChatRequest;
import com.hmdp.service.ConsultantService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatControllerTest {
    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void conversationMemoryIsScopedToAuthenticatedUser() {
        ConsultantService consultant = mock(ConsultantService.class);
        ChatController controller = new ChatController(consultant);
        UserDTO user = new UserDTO();
        user.setId(42L);
        UserHolder.saveUser(user);
        when(consultant.chat("chat:memory:42:session_1", "hello")).thenReturn(Flux.just("ok"));
        var response = controller.chat("session_1", " hello ");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody().blockFirst());
        assertEquals(HttpStatus.BAD_REQUEST, controller.chat("login:token:other", "hello").getStatusCode());
        verify(consultant).chat("chat:memory:42:session_1", "hello");
        verifyNoMoreInteractions(consultant);
    }

    @Test
    void postRequiresAnAuthenticatedUserAndValidSession() {
        ConsultantService consultant = mock(ConsultantService.class);
        ChatController controller = new ChatController(consultant);
        ChatRequest request = new ChatRequest();
        request.setMemoryId("abc");
        request.setMessage("hello");
        assertEquals(HttpStatus.UNAUTHORIZED, controller.chatPost(request).getStatusCode());
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
        when(consultant.chat("chat:memory:7:abc", "hello")).thenReturn(Flux.just("ok"));
        assertEquals("ok", controller.chatPost(request).getBody().blockFirst());
        verify(consultant).chat("chat:memory:7:abc", "hello");
        verifyNoMoreInteractions(consultant);
    }
}
