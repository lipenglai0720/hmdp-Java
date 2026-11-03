package com.hmdp.controller;

import com.hmdp.dto.UserDTO;
import com.hmdp.service.ConsultantService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerHttpTest {
    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void postStreamsTextUsingTheUsersScopedConversation() throws Exception {
        ConsultantService consultant = mock(ConsultantService.class);
        when(consultant.chat("chat:memory:8:session", "hello")).thenReturn(Flux.just("hello ", "world"));
        UserDTO user = new UserDTO();
        user.setId(8L);
        UserHolder.saveUser(user);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatController(consultant)).build();
        var request = mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content("{\"memoryId\":\"session\",\"message\":\"hello\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(request)).andExpect(status().isOk())
                .andExpect(content().string("hello world"));
        verify(consultant).chat("chat:memory:8:session", "hello");
    }
}
