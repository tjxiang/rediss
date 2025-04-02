package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
}
