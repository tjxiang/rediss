package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    UserServiceImpl userService;

    @Override
    public Result follow(Long followId, boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followId);
            follow.setUserId(user.getId());
            boolean success = save(follow);
            if (success) {
                stringRedisTemplate.opsForSet().add("follow:" + user.getId(), followId.toString());
            }
        } else {
            boolean success = remove(new QueryWrapper<Follow>().eq("user_id", user.getId()).eq("follow_user_id", followId));
            if (success) {
                stringRedisTemplate.opsForSet().remove("follow:" + user.getId(), followId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Integer count = query().eq("user_id", UserHolder.getUser().getId()).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        UserDTO user = UserHolder.getUser();
        String key1 = "follow:" + user.getId();
        String key2 = "follow:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (!(intersect.size() > 0)) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map(user1 -> BeanUtil.copyProperties(user1,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
