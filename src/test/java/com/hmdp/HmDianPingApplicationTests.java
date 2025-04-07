package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisIDWorker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;


    @Autowired
    RedisIDWorker redisIDWorker;

    @Autowired
    private CacheUtil cacheUtil;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService pool = Executors.newFixedThreadPool(500);

    @Test
    void test() throws Exception {


        shopService.list().stream().filter(it -> it.getId() != 1).map(Shop::getId).collect(Collectors.toList()).forEach((id) -> {
            Shop shop = shopService.getById(id);
            cacheUtil.setWithLogicalExpire(CACHE_SHOP_KEY + id, shop, 30l, TimeUnit.SECONDS);
        });


//        Shop shop = shopService.getById(1);
//        cacheUtil.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 30l, TimeUnit.SECONDS);
    }

    @Test
    void testWork() throws Exception {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIDWorker.nextId("order:");
                System.out.println("id=" + id);
            }
            latch.countDown();
        };

        long start = System.currentTimeMillis();

        for (int i = 0; i < 300; i++) {
            pool.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("llll:" + (end - start));
    }

    @Test
    public void t() {
        Map<Long, List<Shop>> collect = shopService.list().stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Long id : collect.keySet()) {
            List<Shop> shops = collect.get(id);
            String key = SHOP_GEO_KEY + id;

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());

            shops.forEach(shop -> {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            });

            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    public void tt() {
        String key = "hll1";
        String[] users = new String[1000];
        int index = 0;
        for (int i = 1; i < 1000000; i++) {
            users[index++] = "user_" + i;
            if (i % 1000 == 0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add(key, users);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size(key);
        System.out.println(size);
    }
}
