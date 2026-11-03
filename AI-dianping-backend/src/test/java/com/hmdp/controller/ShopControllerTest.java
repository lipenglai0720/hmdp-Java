package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.ShopAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopControllerTest {
    @Test
    void managerCannotOverwriteSalesAndRatingsThroughShopEditing() {
        IShopService shops = mock(IShopService.class);
        ShopAccess access = mock(ShopAccess.class);
        when(access.canManage(10L)).thenReturn(true);
        when(shops.updateShop(any())).thenAnswer(invocation -> {
            Shop allowed = invocation.getArgument(0);
            assertEquals("新店名", allowed.getName());
            assertNull(allowed.getScore());
            assertNull(allowed.getSold());
            assertNull(allowed.getComments());
            assertNull(allowed.getTypeId());
            return Result.ok();
        });
        Shop incoming = new Shop().setId(10L).setName("新店名").setScore(50).setSold(9999).setComments(9999).setTypeId(2L);
        assertTrue(new ShopController(shops, access).updateShop(incoming).getSuccess());
        verify(shops).updateShop(any());
    }
}
