package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import io.lettuce.core.RedisClient;
import lombok.val;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisIDWorker redisIDWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> seckillScript;

    static {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setLocation(new ClassPathResource("seckill.lua"));
        seckillScript.setResultType(Long.class);
    }

    private BlockingDeque<VoucherOrder> tasks = new LinkedBlockingDeque<>(1024 * 1024);

    private ExecutorService service = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    @PostConstruct
    private void init() {
        service.submit(new VoucherHandle());
    }

    private class VoucherHandle implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder first = tasks.take();
                    hadleVoucherOrder(first);
                } catch (Exception e) {
                    log.error("bu xing ");
                }
            }
        }
    }

    private void hadleVoucherOrder(VoucherOrder first) {
        Long userId = first.getUserId();

        // 分布式锁
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = false;
        try {
            isLock = lock.tryLock();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!isLock) {
            log.error("存储失败");
        }
        try {
            proxy.createVoucherOrder(first);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

    }

    //    @Override
//    public Result seckillVoucher(Long id) {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);
//        if (seckillVoucher == null) {
//            return Result.fail("没有优惠券");
//        }
//
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("尚未开始");
//        }
//
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("已经结束");
//        }
//
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        // 分布式锁
////        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        boolean isLock = false;
//        try {
//            isLock = lock.tryLock(1, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        if (!isLock) {
//             return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService v = (IVoucherOrderService) AopContext.currentProxy();
//            return v.createVoucherOrder(id);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        }finally {
//            lock.unlock();
//        }
//
//
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService v = (IVoucherOrderService)AopContext.currentProxy();
////            return v.createVoucherOrder(id);
////        }
//
//
//    }
    @Override
    public Result seckillVoucher(Long id) {

        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(seckillScript, Collections.emptyList(), id.toString(), userId.toString());
        if (result.intValue() != 0) {
            return Result.fail(result.intValue() == 1 ? "不足" : "重复");
        }


        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(id);
        tasks.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucher) {

        Long userId = UserHolder.getUser().getId();

//        synchronized (userId) {
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucher.getVoucherId()).count();

        if (count > 0) {
//            return Result.fail("买过了");
            log.error("买过了");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucher.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
//            return Result.fail("库存不足");
            log.error("库存不足");
        }
//        int i=1/0;
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIDWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucher.getVoucherId());
        save(voucher);
//        return Result.ok(orderId);
//        }
    }
}
