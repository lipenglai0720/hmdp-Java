package com.hmdp.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String memoryId;
    private String message;
}
