package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String shops = stringRedisTemplate.opsForValue().get("type_list");
        if (!StringUtils.isEmpty(shops)) {
            return Result.ok(JSONUtil.toList(shops,ShopType.class));
        }

        List<ShopType> typeList = query().orderByAsc("sort").list();

        if (typeList == null) {
            return Result.fail("没有type/list");
        }

        stringRedisTemplate.opsForValue().set("shop_type_list:", JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }
}
