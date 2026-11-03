package com.hmdp.controller;

import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.ShopAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoucherControllerTest {
    private IVoucherService vouchers;
    private ShopAccess access;
    private VoucherController controller;

    @BeforeEach
    void setUp() {
        vouchers = mock(IVoucherService.class);
        access = mock(ShopAccess.class);
        controller = new VoucherController(vouchers, access);
    }

    @Test
    void rejectsUnassignedMerchantAndInvalidMoney() {
        Voucher voucher = new Voucher().setShopId(10L).setTitle("午餐优惠")
                .setPayValue(6000L).setActualValue(5000L);
        assertFalse(controller.addVoucher(voucher).getSuccess());
        when(access.canManage(10L)).thenReturn(true);
        assertFalse(controller.addVoucher(voucher).getSuccess());
        verifyNoInteractions(vouchers);
    }

    @Test
    void createsOnlyAnEnabledOrdinaryVoucherForAnAssignedShop() {
        Voucher voucher = new Voucher().setId(55L).setShopId(10L).setTitle(" 午餐优惠 ")
                .setPayValue(4000L).setActualValue(5000L).setType(1).setStatus(2);
        when(access.canManage(10L)).thenReturn(true);
        when(vouchers.save(any(Voucher.class))).thenAnswer(invocation -> {
            Voucher saved = invocation.getArgument(0);
            assertNull(saved.getId());
            assertEquals(0, saved.getType());
            assertEquals(1, saved.getStatus());
            assertEquals("午餐优惠", saved.getTitle());
            saved.setId(123L);
            return true;
        });
        assertEquals(123L, controller.addVoucher(voucher).getData());
    }
}
