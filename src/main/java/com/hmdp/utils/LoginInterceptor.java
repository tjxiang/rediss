package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import net.sf.jsqlparser.statement.select.KSQLWindow;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

public class LoginInterceptor implements HandlerInterceptor {
//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        HttpSession session = request.getSession();
//
//        String token = request.getHeader("authorization");
//        System.out.println(request.getRequestURI());
//        if (StringUtils.isEmpty(token)){
//            response.setStatus(401);
//            return false;
//        }
//
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        if (userMap.isEmpty()){
//            response.setStatus(401);
//            return false;
//        }
//
//
//
//        UserHolder.saveUser(BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false));
//
////        UserDTO user = (UserDTO) session.getAttribute("user");
////        if (user == null){
////            response.setStatus(401);
////            return false;
////        }
//
//
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token,30, TimeUnit.MINUTES);

        if (UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
