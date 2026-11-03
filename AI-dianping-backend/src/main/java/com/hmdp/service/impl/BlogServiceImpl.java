package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 * @author 超大王
 * @since 2025-09-25
 */
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource private RedissonClient redissonClient;
    @Resource private com.hmdp.service.IShopService shopService;

    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final IFollowService followService;

    public BlogServiceImpl(IUserService userService, StringRedisTemplate stringRedisTemplate, IFollowService followService) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.followService = followService;
    }

    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在或已移除");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        // 3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        if (current == null || current < 1) current = 1;
        if (current > 10000) return Result.fail("页码过大");
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .orderByDesc("id")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogsByShop(Long shopId, Integer current) {
        if (shopId == null || shopId <= 0) return Result.fail("店铺信息无效");
        int pageNumber = current == null || current < 1 ? 1 : current;
        if (pageNumber > 10000) return Result.fail("页码过大");
        Page<Blog> page = lambdaQuery()
                .eq(Blog::getShopId, shopId)
                .orderByDesc(Blog::getCreateTime)
                .orderByDesc(Blog::getId)
                .page(new Page<>(pageNumber, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        List<Long> userIds = blogs.stream().map(Blog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> authors = userIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        blogs.forEach(blog -> {
            User author = authors.get(blog.getUserId());
            if (author != null) {
                blog.setName(author.getNickName());
                blog.setIcon(author.getIcon());
            }
        });
        return Result.ok(blogs, page.getTotal());
    }

    @Override
    public Result likeBlog(Long id) {
        if (id == null || id <= 0 || getById(id) == null) return Result.fail("笔记不存在");
        if (UserHolder.getUser() == null) return Result.fail("请先登录");
        RLock lock = redissonClient.getLock("lock:blog:like:" + id + ":" + UserHolder.getUser().getId());
        boolean locked = false;
        try {
            locked = lock.tryLock(2, TimeUnit.SECONDS);
            if (!locked) return Result.fail("操作过于频繁，请稍后重试");
            return toggleLike(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("操作被中断，请重试");
        } finally { if (locked && lock.isHeldByCurrentThread()) lock.unlock(); }
    }

    private Result toggleLike(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.如果未点赞，可以点赞
        if(score == null){
            //未点赞，可以点赞
            //数据库点赞数+1
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if(success){
                //保存用户到Redis的set集合
                stringRedisTemplate.opsForZSet().add(key,userId.toString(), System.currentTimeMillis());//时间戳
            }
        } else {
            //4.如果已经点赞，取消点赞
            boolean success = update().setSql("liked = GREATEST(0, liked - 1)").eq("id", id).update();
            if(success){
                //保存用户到Redis的set集合
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> topFive = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(topFive == null || topFive.isEmpty()){
            return Result.ok();
        }
        // 2.解析出其中的用户id
        List<Long> ids = topFive.stream()
                .map(Long::valueOf)
                .toList();
        String idStr = StrUtil.join(",", ids);//用逗号拼接字符串
        // 3.根据用户id查询用户 WHERE id IN (5,1,2) ORDER BY FIELD(id, 5,1,2)
        List<UserDTO> userDTOS = userService
                .query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()//last拼接sql语句
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        if (UserHolder.getUser() == null) return Result.fail("请先登录");
        if (blog == null || blog.getShopId() == null || shopService.getById(blog.getShopId()) == null)
            return Result.fail("请选择有效的关联店铺");
        if (blog.getTitle() == null || blog.getTitle().isBlank() || blog.getTitle().length() > 255
                || blog.getContent() == null || blog.getContent().isBlank() || blog.getContent().length() > 2048
                || (blog.getImages() != null && (blog.getImages().length() > 2048 || blog.getImages().split(",").length > 9)))
            return Result.fail("标题不超过255字，正文不超过2048字，照片最多9张");
        blog = new Blog().setShopId(blog.getShopId()).setTitle(blog.getTitle().trim())
                .setContent(blog.getContent().trim()).setImages(blog.getImages() == null ? "" : blog.getImages())
                .setLiked(0).setComments(0);
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        //2.保存探店笔记
        boolean success = save(blog);
        if(!success){
            return Result.fail("笔记保存失败咯~");
        }
        // 3.查询笔记作者的所有粉丝
        List<Follow> followedUsers = followService.query().eq("follow_user_id", userId).list();
        // 4.推送笔记id给所有粉丝
        for(Follow followedUser : followedUsers){
            Long followId = followedUser.getUserId();
            String key = RedisConstants.FEED_KEY + followId;
            try {
                stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
                stringRedisTemplate.opsForZSet().removeRange(key, 0, -1001);
            } catch (RuntimeException e) { log.warn("笔记已保存，关注动态推送暂时失败，blogId={}",blog.getId()); }
        }
        return Result.ok(blog.getId());
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //用户未登录，无需查询
            return;
        }
        Long userId = user.getId();
        if (userId == null) {
            // 用户ID为空，同样无需查询
            return;
        }
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score != null){
            blog.setIsLike(true);
        }
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        if (max == null || max < 0 || offset == null || offset < 0 || offset > 10000) return Result.fail("分页参数无效");
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key max min WITHSCORES LIMIT offset count
        /*
         * key: 你要查询的有序集合的键名。在你的代码场景中，这可能是某个用户的 "收件箱"，例如 feed:userId。
         * max: 分数区间的最大值。命令会返回分数小于或等于 max 的成员。
         * min: 分数区间的最小值。命令会返回分数大于或等于 min 的成员。
         * [WITHSCORES] (可选): 如果加上这个选项，返回结果中不仅会包含成员（member），
         * 还会包含该成员对应的分数（score）。返回的列表会是 [member1, score1, member2, score2, ...] 的形式。
         * [LIMIT offset count] (可选): 这个选项用于分页。
         * offset: 表示从符合条件的成员中，跳过前 offset 个。
         * count: 表示在跳过 offset 个之后，最多返回 count 个成员
         */
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        // 4.解析数据：blogId、minTime（时间戳）、offset（偏移量）
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1; //偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
            // 4.1.获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            // 4.2.获取分数（时间戳）
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os + offset : os;
        /*
          在 max 这个时间点上，有非常多的数据，一页都装不下。
          所以下一次查询仍然需要从 max 这个时间点开始，但是需要跳过更多的数据。
          跳过的数量就是 os + offset（上次跳过的数量 + 这次新获取的数量）。
         */

        // 5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        blogs.stream().forEach(blog -> {
            // 5.1.查询blog有关的用户
            queryBlogUser(blog);
            // 5.2.查询blog是否被点赞
            isBlogLiked(blog);
        });

        // 6.封装并返回
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(os);
        return Result.ok(result);
    }
}
