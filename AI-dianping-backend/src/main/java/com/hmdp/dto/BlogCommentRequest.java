package com.hmdp.dto;

import lombok.Data;

@Data
public class BlogCommentRequest {
    private Long blogId;
    private String content;
}
