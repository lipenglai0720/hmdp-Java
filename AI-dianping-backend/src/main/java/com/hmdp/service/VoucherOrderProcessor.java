package com.hmdp.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VoucherOrderProcessor {
    private final VoucherOrderMapper orders;
    private final ISeckillVoucherService stock;
    private final RedissonClient redisson;
    private final TransactionTemplate transactions;
    public record Outcome(String code, String message, String existingOrderId) {}

    public Outcome create(VoucherOrder order, boolean seckill) {
        if(order.getId()==null||order.getUserId()==null||order.getVoucherId()==null)
            throw new IllegalArgumentException("订单消息缺少必要字段");
        RLock lock=redisson.getLock("lock:order:"+order.getUserId());
        boolean locked=false;
        try {
            locked=lock.tryLock(2,TimeUnit.SECONDS);
            if(!locked) throw new IllegalStateException("用户订单正在处理");
            return transactions.execute(tx -> {
                VoucherOrder existing=orders.selectOne(new QueryWrapper<VoucherOrder>()
                        .eq("user_id",order.getUserId()).eq("voucher_id",order.getVoucherId()).last("LIMIT 1"));
                if(existing!=null) return new Outcome(existing.getId().equals(order.getId())?"CREATED":"DUPLICATE",
                        "该优惠券已经领取",existing.getId().toString());
                if(seckill&&!stock.update().setSql("stock = stock - 1")
                        .eq("voucher_id",order.getVoucherId()).gt("stock",0).update())
                    return new Outcome("SOLD_OUT","优惠券已售罄，抢购未成功","");
                order.setStatus(1);
                if(orders.insert(order)!=1) throw new IllegalStateException("订单写入失败");
                return new Outcome("CREATED","订单已创建",order.getId().toString());
            });
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("订单处理被中断",e);
        } finally {
            if(locked&&lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}

