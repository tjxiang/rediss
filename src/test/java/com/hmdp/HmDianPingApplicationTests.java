package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisIDWorker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;


    @Autowired
    RedisIDWorker redisIDWorker;

    @Autowired
    private CacheUtil cacheUtil;

    private static final ExecutorService pool = Executors.newFixedThreadPool(500);

    @Test
    void test() throws Exception {
        Shop shop = shopService.getById(1);
        cacheUtil.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 30l, TimeUnit.SECONDS);
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
        System.out.println("llll:" + (end-start));
    }
}
