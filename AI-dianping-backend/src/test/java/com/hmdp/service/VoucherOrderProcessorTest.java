package com.hmdp.service;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.entity.*;
import com.hmdp.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoucherOrderProcessorTest {
    VoucherOrderMapper orders; ISeckillVoucherService stock; UpdateChainWrapper<SeckillVoucher> update;
    RLock lock; VoucherOrderProcessor processor;
    @BeforeEach @SuppressWarnings("unchecked") void init() throws Exception {
        orders=mock(VoucherOrderMapper.class); stock=mock(ISeckillVoucherService.class);
        update=mock(UpdateChainWrapper.class,RETURNS_SELF); when(stock.update()).thenReturn(update);
        when(update.setSql(anyString(), any(Object[].class))).thenReturn(update);
        when(update.eq(anyString(), any())).thenReturn(update);
        when(update.gt(anyString(), any())).thenReturn(update);
        RedissonClient redisson=mock(RedissonClient.class);lock=mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(lock);when(lock.tryLock(2,TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        processor=new VoucherOrderProcessor(orders,stock,redisson,new TransactionTemplate(transactions));
    }
    VoucherOrder order() { return new VoucherOrder().setId(100L).setUserId(7L).setVoucherId(9L); }
    @Test void duplicateDeliveryDoesNotDeductStockAgain() {
        when(orders.selectOne(any())).thenReturn(order());
        assertEquals("CREATED",processor.create(order(),true).code());
        verifyNoInteractions(stock);verify(orders,never()).insert(any(VoucherOrder.class));
    }
    @Test void aSecondOrderForTheSameUserAndVoucherIsRejectedByTheDatabaseCheck() {
        when(orders.selectOne(any())).thenReturn(order().setId(99L));
        assertEquals("DUPLICATE",processor.create(order(),true).code());
        verifyNoInteractions(stock);verify(orders,never()).insert(any(VoucherOrder.class));
    }
    @Test void databaseStockGuardPreventsOverselling() {
        when(update.update()).thenReturn(false);
        assertEquals("SOLD_OUT",processor.create(order(),true).code());
        verify(orders,never()).insert(any(VoucherOrder.class));
    }
    @Test void successfulSeckillWritesOnlyAfterStockWasDeducted() {
        when(update.update()).thenReturn(true);when(orders.insert(any(VoucherOrder.class))).thenReturn(1);
        VoucherOrder order=order();assertEquals("CREATED",processor.create(order,true).code());
        var sequence=inOrder(update,orders);sequence.verify(update).update();sequence.verify(orders).insert(order);
        assertEquals(1,order.getStatus());verify(lock).unlock();
    }
    @Test void ordinaryClaimDoesNotTouchSeckillInventory() {
        when(orders.insert(any(VoucherOrder.class))).thenReturn(1);
        assertEquals("CREATED",processor.create(order(),false).code());verifyNoInteractions(stock);
    }
    @Test void lockContentionDoesNotWriteAnOrder() throws Exception {
        when(lock.tryLock(2,TimeUnit.SECONDS)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->processor.create(order(),true));verifyNoInteractions(orders,stock);
        verify(lock,never()).unlock();
    }
}
