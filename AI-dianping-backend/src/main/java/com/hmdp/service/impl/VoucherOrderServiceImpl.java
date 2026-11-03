package com.hmdp.service.impl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.*;
import com.hmdp.entity.*;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.*;
import com.hmdp.utils.*;
import jakarta.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper,VoucherOrder> implements IVoucherOrderService {
    public static final String STREAM="stream.orders", GROUP="g1", RESULT_KEY="seckill:result:";
    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private IVoucherService voucherService;
    @Resource private IShopService shopService;
    @Resource private RedisIdWorker redisIdWorker;
    @Resource private RedissonClient redissonClient;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private VoucherOrderProcessor processor;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final String consumer="order-"+UUID.randomUUID();
    private static DefaultRedisScript<Long> script(String path) {
        var script=new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource(path)); script.setResultType(Long.class); return script;
    }
    private static final DefaultRedisScript<Long> SECKILL=script("seckill.lua");
    private static final DefaultRedisScript<Long> INITIALIZE=script("seckill_init.lua");
    private static final DefaultRedisScript<Long> COMPLETE=script("seckill_complete.lua");

    @EventListener(ApplicationStartedEvent.class)
    public void init() {
        if(!running.compareAndSet(false,true)) return;
        initializeGroup();
        for(SeckillVoucher voucher:seckillVoucherService.list()) initializeStock(voucher.getVoucherId());
        executor.submit(this::consume);
    }

    private void initializeGroup() {
        try { stringRedisTemplate.opsForStream().createGroup(STREAM,ReadOffset.from("0"),GROUP); }
        catch(Exception e) {
            if(hasCause(e,"BUSYGROUP")) return;
            stringRedisTemplate.opsForStream().add(STREAM,Map.of("type","bootstrap"));
            try { stringRedisTemplate.opsForStream().createGroup(STREAM,ReadOffset.from("0"),GROUP); }
            catch(Exception retry) { if(!hasCause(retry,"BUSYGROUP")) throw retry; }
        }
    }
    private boolean hasCause(Throwable e,String text) {
        for(Throwable t=e;t!=null;t=t.getCause()) { if(t.getMessage()!=null&&t.getMessage().contains(text)) return true; if(t==t.getCause()) break; }
        return false;
    }

    /** Redis 数据丢失时从数据库恢复剩余库存及已购用户，初始化期间使用分布式锁。 */
    private void initializeStock(Long voucherId) {
        String stockKey=RedisConstants.SECKILL_STOCK_KEY+voucherId;
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) return;
        RLock lock=redissonClient.getLock("lock:stock:init:"+voucherId);
        lock.lock();
        try {
            if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) return;
            SeckillVoucher voucher=seckillVoucherService.getById(voucherId);
            if(voucher==null||voucher.getStock()==null) return;
            List<String> args=new ArrayList<>(); args.add(Integer.toString(Math.max(0,voucher.getStock())));
            lambdaQuery().eq(VoucherOrder::getVoucherId,voucherId).select(VoucherOrder::getUserId).list()
                    .forEach(o -> args.add(o.getUserId().toString()));
            stringRedisTemplate.execute(INITIALIZE,List.of(stockKey,"seckill:order:"+voucherId),args.toArray());
        } finally { if(lock.isHeldByCurrentThread()) lock.unlock(); }
    }

    private void consume() {
        long nextRecovery=0;
        while(running.get()&&!Thread.currentThread().isInterrupted()) {
            try {
                if(System.nanoTime()>=nextRecovery) {
                    recoverPending(); nextRecovery=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                }
                List<MapRecord<String,Object,Object>> records=stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP,consumer),StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM,ReadOffset.lastConsumed()));
                if(records!=null) for(var record:records) handle(record);
            } catch(Exception e) {
                if(!running.get()||Thread.currentThread().isInterrupted()) break;
                log.warn("订单消费暂不可用，稍后重试",e);
                try { Thread.sleep(1000); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
    }
    private void recoverPending() {
        var pending=stringRedisTemplate.opsForStream().pending(STREAM,GROUP,Range.unbounded(),50);
        if(pending==null) return;
        for(var message:pending) {
            if(message.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofSeconds(30))<0) continue;
            List<MapRecord<String,Object,Object>> claimed=stringRedisTemplate.opsForStream()
                    .claim(STREAM,GROUP,consumer,Duration.ofSeconds(30),message.getId());
            if(claimed!=null) for(var record:claimed) handle(record);
        }
    }
    void handle(MapRecord<String,Object,Object> record) {
        try {
            Map<Object,Object> value=record.getValue();
            if("bootstrap".equals(value.get("type"))) {
                stringRedisTemplate.opsForStream().acknowledge(STREAM,GROUP,record.getId()); return;
            }
            VoucherOrder order=new VoucherOrder().setId(positive(value,"orderId"))
                    .setUserId(positive(value,"userId")).setVoucherId(positive(value,"voucherId"));
            var outcome=processor.create(order,true);
            stringRedisTemplate.execute(COMPLETE,List.of(RedisConstants.SECKILL_STOCK_KEY+order.getVoucherId(),
                            "seckill:order:"+order.getVoucherId(),RESULT_KEY+order.getId()),
                    order.getUserId().toString(),order.getVoucherId().toString(),order.getId().toString(),
                    outcome.code(),outcome.message(),outcome.existingOrderId());
            stringRedisTemplate.opsForStream().acknowledge(STREAM,GROUP,record.getId());
        } catch(IllegalArgumentException e) {
            stringRedisTemplate.opsForStream().add(STREAM+".dlq",Map.of("sourceId",record.getId().toString(),"reason","订单消息字段无效"));
            stringRedisTemplate.opsForStream().acknowledge(STREAM,GROUP,record.getId());
            log.error("无效订单消息已隔离：{}",record.getId());
        } catch(Exception e) { log.warn("订单消息处理失败，将在稍后重试：{}",record.getId(),e); }
    }
    private Long positive(Map<Object,Object> value,String key) {
        try { long id=Long.parseLong(Objects.toString(value.get(key),"")); if(id>0) return id; }
        catch(NumberFormatException ignored) { }
        throw new IllegalArgumentException("订单字段无效");
    }

    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public void onDestroy() {
        running.set(false); executor.shutdownNow();
        try { executor.awaitTermination(3,TimeUnit.SECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        if(voucherId==null||voucherId<=0||UserHolder.getUser()==null) return Result.fail("请先登录并选择有效优惠券");
        Voucher voucher=voucherService.getById(voucherId);
        SeckillVoucher seckill=seckillVoucherService.getById(voucherId);
        if(voucher==null||!Integer.valueOf(1).equals(voucher.getType())||!Integer.valueOf(1).equals(voucher.getStatus())
                ||seckill==null||seckill.getBeginTime()==null||seckill.getEndTime()==null) return Result.fail("优惠券不可用");
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        LocalDateTime now=LocalDateTime.now(zone);
        if(now.isBefore(seckill.getBeginTime())) return Result.fail("活动尚未开始");
        if(!now.isBefore(seckill.getEndTime())) return Result.fail("活动已经结束");
        initializeStock(voucherId);
        long orderId=redisIdWorker.nextId("order");
        Long code=stringRedisTemplate.execute(SECKILL,List.of(RedisConstants.SECKILL_STOCK_KEY+voucherId,
                        "seckill:order:"+voucherId,STREAM,RESULT_KEY+orderId),
                UserHolder.getUser().getId().toString(),voucherId.toString(),Long.toString(orderId),
                Long.toString(seckill.getBeginTime().atZone(zone).toInstant().toEpochMilli()),
                Long.toString(seckill.getEndTime().atZone(zone).toInstant().toEpochMilli()));
        if(code==null) return Result.fail("抢购服务暂时不可用");
        return switch(code.intValue()) {
            case 0 -> Result.ok(Long.toString(orderId));
            case 1 -> Result.fail("库存不足");
            case 2 -> Result.fail("每人限领一张，请在我的订单查看");
            case 4 -> Result.fail("活动尚未开始");
            case 5 -> Result.fail("活动已经结束");
            default -> Result.fail("抢购服务暂时不可用，请稍后重试");
        };
    }

    @Override
    public Result claimVoucher(Long voucherId) {
        if(UserHolder.getUser()==null) return Result.fail("请先登录");
        Voucher voucher=voucherId==null?null:voucherService.getById(voucherId);
        if(voucher==null||!Integer.valueOf(0).equals(voucher.getType())||!Integer.valueOf(1).equals(voucher.getStatus()))
            return Result.fail("优惠券不可领取");
        var order=new VoucherOrder().setId(redisIdWorker.nextId("order")).setUserId(UserHolder.getUser().getId()).setVoucherId(voucherId);
        var result=processor.create(order,false);
        if("DUPLICATE".equals(result.code())) return Result.fail("已经领取过该优惠券，请在我的订单查看");
        return Result.ok(order.getId().toString());
    }

    @Override
    public Result queryOrderResult(Long id) {
        if(UserHolder.getUser()==null) return Result.fail("请先登录");
        VoucherOrder order=getById(id);
        if(order!=null) {
            if(!order.getUserId().equals(UserHolder.getUser().getId())) return Result.fail("订单不存在");
            return Result.ok(Map.of("orderId",order.getId().toString(),"status","CREATED","message","订单已创建"));
        }
        Map<Object,Object> result=stringRedisTemplate.opsForHash().entries(RESULT_KEY+id);
        if(!UserHolder.getUser().getId().toString().equals(result.get("userId"))) return Result.fail("订单不存在或结果已过期");
        Map<String,Object> safe=new LinkedHashMap<>();
        for(String key:List.of("orderId","status","message","existingOrderId")) if(result.containsKey(key)) safe.put(key,result.get(key));
        return Result.ok(safe);
    }

    @Override
    public Result queryMyOrders(Integer current) {
        if(UserHolder.getUser()==null) return Result.fail("请先登录");
        int number=current==null||current<1?1:current;
        if(number>10000) return Result.fail("页码过大");
        Page<VoucherOrder> page=lambdaQuery().eq(VoucherOrder::getUserId,UserHolder.getUser().getId())
                .orderByDesc(VoucherOrder::getCreateTime).orderByDesc(VoucherOrder::getId)
                .page(new Page<>(number,SystemConstants.MAX_PAGE_SIZE));
        List<Long> voucherIds=page.getRecords().stream().map(VoucherOrder::getVoucherId).distinct().toList();
        Map<Long,Voucher> vouchers=voucherIds.isEmpty()?Map.of():voucherService.listByIds(voucherIds).stream().collect(Collectors.toMap(Voucher::getId,v->v));
        List<Long> shopIds=vouchers.values().stream().map(Voucher::getShopId).distinct().toList();
        Map<Long,Shop> shops=shopIds.isEmpty()?Map.of():shopService.listByIds(shopIds).stream().collect(Collectors.toMap(Shop::getId,s->s));
        List<VoucherOrderView> views=page.getRecords().stream().map(o -> {
            var v=new VoucherOrderView(); v.setId(o.getId().toString()); v.setVoucherId(o.getVoucherId());
            v.setStatus(o.getStatus()); v.setCreateTime(o.getCreateTime());
            Voucher voucher=vouchers.get(o.getVoucherId());
            if(voucher!=null) { v.setVoucherTitle(voucher.getTitle()); v.setShopId(voucher.getShopId()); v.setPayValue(voucher.getPayValue());
                Shop shop=shops.get(voucher.getShopId()); if(shop!=null) v.setShopName(shop.getName()); }
            return v;
        }).toList();
        return Result.ok(views,page.getTotal());
    }
}

