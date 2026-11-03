package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.BlogCommentRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {
    private final IBlogService blogService;
    private final IUserService userService;

    @Override
    public Result queryComments(Long blogId, Integer current) {
        if (blogId == null || blogId <= 0) return Result.fail("笔记信息无效");
        int number = current == null || current < 1 ? 1 : current;
        if (number > 10000) return Result.fail("页码过大");
        Page<BlogComments> page = lambdaQuery().eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getStatus, 0).eq(BlogComments::getParentId, 0)
                .orderByDesc(BlogComments::getCreateTime).orderByDesc(BlogComments::getId)
                .page(new Page<>(number, SystemConstants.MAX_PAGE_SIZE));
        List<BlogComments> items = page.getRecords();
        List<Long> userIds = items.stream().map(BlogComments::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        items.forEach(item -> {
            User user = users.get(item.getUserId());
            if (user != null) {
                item.setAuthorName(user.getNickName());
                item.setAuthorIcon(user.getIcon());
            }
        });
        return Result.ok(items, page.getTotal());
    }

    @Override
    @Transactional
    public Result addComment(BlogCommentRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) return Result.fail("请先登录");
        if (request == null || request.getBlogId() == null || request.getBlogId() <= 0) return Result.fail("笔记信息无效");
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty() || content.length() > 255) return Result.fail("评论请输入1至255个字");
        if (blogService.getById(request.getBlogId()) == null) return Result.fail("笔记不存在");
        BlogComments item = new BlogComments().setBlogId(request.getBlogId()).setUserId(user.getId())
                .setParentId(0L).setAnswerId(0L).setContent(content).setLiked(0).setStatus(0);
        if (!save(item)) throw new IllegalStateException("评论保存失败");
        boolean updated = blogService.update().setSql("comments = COALESCE(comments, 0) + 1")
                .eq("id", request.getBlogId()).update();
        if (!updated) throw new IllegalStateException("笔记评论数量更新失败");
        return Result.ok(item.getId());
    }
}
