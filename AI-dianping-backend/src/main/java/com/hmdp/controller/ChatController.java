package com.hmdp.controller;

import com.hmdp.dto.UserDTO;
import com.hmdp.dto.ChatRequest;
import com.hmdp.service.ConsultantService;
import com.hmdp.utils.UserHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
public class ChatController {
    private final ConsultantService consultantService;

    public ChatController(ConsultantService consultantService) {
        this.consultantService = consultantService;
    }

    @GetMapping(produces = "text/plain;charset=utf-8")
    public ResponseEntity<Flux<String>> chat(@RequestParam("memoryId") String memoryId,
                                             @RequestParam("message") String message) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (memoryId == null || !memoryId.matches("[A-Za-z0-9_-]{1,64}")
                || message == null || message.isBlank() || message.length() > 2000) {
            return ResponseEntity.badRequest().body(Flux.just("请输入有效的对话内容"));
        }
        String scopedMemoryId = "chat:memory:" + user.getId() + ":" + memoryId;
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").header("Cache-Control", "no-store")
                .body(consultantService.chat(scopedMemoryId, message.trim())
                        .onErrorResume(error -> Flux.just("\n对话服务暂时不可用，请稍后重试。你也可以在业务页面查询预约、订单和优惠。")));
    }

    @PostMapping(produces = "text/plain;charset=utf-8")
    public ResponseEntity<Flux<String>> chatPost(@RequestBody ChatRequest request) {
        if (request == null) return ResponseEntity.badRequest().body(Flux.just("请输入有效的对话内容"));
        return chat(request.getMemoryId(), request.getMessage());
    }
}
