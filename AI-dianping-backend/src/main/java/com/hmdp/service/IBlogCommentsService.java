package com.hmdp.service;

import com.hmdp.entity.BlogComments;
import com.hmdp.dto.BlogCommentRequest;
import com.hmdp.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {
    Result queryComments(Long blogId, Integer current);
    Result addComment(BlogCommentRequest request);
}
