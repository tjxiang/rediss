package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import jodd.util.CollectionUtil;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.jws.soap.SOAPBinding;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("phone error");
        }
        String code = RandomUtil.randomNumbers(4);

//        session.setAttribute("code", code);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("code:" + code);

        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("phone error");
        }
//        String code = session.getAttribute("code").toString();
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("code error");
        }
        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = createUser(phone);
        }
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((name, field) -> field.toString())));
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("user is null");
        }

        String prefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + prefix;

        int dayOfMonth = LocalDateTime.now().getDayOfMonth();

        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("user is null");
        }

        String prefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + prefix;

        int dayOfMonth = LocalDateTime.now().getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().
                                get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0));

        if (CollectionUtils.isEmpty(result)){
            return Result.ok(0);
        }

        Long num = result.get(0);
        if (num == 0 || num==null) {
            return Result.ok(0);
        }
        int count=0;
        while (true){
            if ((num & 1)==0) {
                break;
            }else {
                count++;
            }
            num>>>=1;
        }

        return Result.ok(count);
    }

    private User createUser(String phone) {
        User u = new User();
        u.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        u.setPhone(phone);
        save(u);
        return u;
    }
}
