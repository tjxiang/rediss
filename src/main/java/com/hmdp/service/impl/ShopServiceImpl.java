package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import net.sf.jsqlparser.statement.select.KSQLWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    CacheUtil cacheUtil;

    @Override
    public Result queryShopById(Long id) {

//        Shop shop = cacheUtil.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, ids -> getById(ids), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheUtil.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, (ids) ->
                        getById(ids)
                , CACHE_SHOP_TTL, TimeUnit.MINUTES);


//        Shop shop = queryWithMutex(id);
//
//        if (shop == null){
//            return Result.fail("不存在");
//        }

//        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("不存在");
        }
        return Result.ok(shop);
    }

    //穿透
    private Result queryWithPassThrough(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (!StringUtils.isEmpty(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }

        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "");
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商户不在");
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    private Shop queryWithMutex(Long id) {
        Shop shop = null;
        try {
            String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            if (shopJson != null) {
                return null;
            }

            Boolean flag = tryLock(LOCK_SHOP_KEY + id);
            if (!flag) {
                Thread.sleep(50);
                queryWithMutex(id);
            }

            Thread.sleep(300);

            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "");
                stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            unLock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    private Shop queryWithLogicalExpire(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        Boolean flag = tryLock(LOCK_SHOP_KEY + id);
        if (BooleanUtil.isTrue(flag)) {
            pool.submit(() -> {
                try {
                    saveShop2(id, 20);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(LOCK_SHOP_KEY + id);
                }

            });
        }
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        return shop;
    }

    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public void saveShop2(long id, int expire) throws Exception {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expire));

        Thread.sleep(200);

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("没有");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
//
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content =
                results.getContent();

        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(from).forEach(it -> {
            String shopIdStr = it.getContent().getName();
            ids.add(Long.parseLong(shopIdStr));
            Distance distance = it.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        String string = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids)
                .last("order by field(id," + string + ")")
                .list();

        shops.forEach(s -> {
            Distance distance = distanceMap.get(s.getId().toString());
            double value = distance.getValue();
            s.setDistance(value);
        });

        return Result.ok(shops);
    }

    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private Boolean unLock(String key) {
        Boolean flag = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(flag);
    }
}
