package com.hmdp.controller;

import com.hmdp.dto.BlogCommentRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog-comments")
@RequiredArgsConstructor
public class BlogCommentsController {
    private final IBlogCommentsService commentsService;

    @GetMapping("/of/blog")
    public Result list(@RequestParam Long blogId,
                       @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentsService.queryComments(blogId, current);
    }

    @PostMapping
    public Result create(@RequestBody BlogCommentRequest request) {
        return commentsService.addComment(request);
    }
}
