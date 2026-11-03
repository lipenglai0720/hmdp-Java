package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 超大王
 * @since 2025-09-25
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, IUserService userService) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result isFollow(Long id) {
        // 1.获取登录用户Id
        Long userId = UserHolder.getUser().getId();
        // 2.查询是否关注
        Long count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        if (id == null || id <= 0 || id.equals(userId)) return Result.fail("请选择其他用户");
        if (userService.getById(id) == null) return Result.fail("用户不存在");
        String key = "follows:" + userId;
        // 2.判断是关注还是取关
        if(isFollow){
            if (query().eq("user_id", userId).eq("follow_user_id", id).count() > 0) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
                return Result.ok();
            }
            // 3.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean success;
            try { success = save(follow); }
            catch (DuplicateKeyException e) { success = true; }
            if(success){
                //把关注用户的id放到redis的set集合中
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            // 4.取关，删除数据
            boolean success = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if(success){
                //把关注用户的id从redis的set集合中移除
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followCommons(Long id) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        Set<Long> mine = lambdaQuery().eq(Follow::getUserId, userId).list().stream()
                .map(Follow::getFollowUserId).collect(Collectors.toSet());
        if(mine.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = lambdaQuery().eq(Follow::getUserId, id).list().stream()
                .map(Follow::getFollowUserId).filter(mine::contains).distinct().toList();
        if (ids.isEmpty()) return Result.ok(Collections.emptyList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(userDTOS);
    }

}
