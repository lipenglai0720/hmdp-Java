package com.hmdp.service;

import com.hmdp.dto.*;
import com.hmdp.tools.OrderTool;
import com.hmdp.utils.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolUserContextTest {
    @AfterEach void clear() { UserHolder.removeUser(); }
    @Test void restoresIdentityOnAnAsyncToolThreadAndRemovesItAfterward() {
        IVoucherOrderService orders = mock(IVoucherOrderService.class);
        when(orders.queryMyOrders(1)).thenAnswer(call -> {
            assertEquals(42L, UserHolder.getUser().getId());
            return Result.ok("owned-by-42");
        });
        String result = CompletableFuture.supplyAsync(() -> {
            assertNull(UserHolder.getUser());
            String text = new OrderTool(orders).queryMyOrders("chat:memory:42:abc", 1);
            assertNull(UserHolder.getUser());
            return text;
        }).join();
        assertTrue(result.contains("owned-by-42"));
    }
    @Test void refusesUnscopedMemoryAndRestoresThePreviousUserEvenOnFailure() {
        assertFalse(ToolUserContext.call("42", () -> fail("must not execute")).getSuccess());
        UserDTO old = new UserDTO(); old.setId(5L); UserHolder.saveUser(old);
        assertThrows(IllegalStateException.class, () -> ToolUserContext.call("chat:memory:42:abc", () -> {
            assertEquals(42L,UserHolder.getUser().getId()); throw new IllegalStateException("test");
        }));
        assertEquals(5L, UserHolder.getUser().getId());
    }
    @Test void reservationToolUsesTheFrameworkMemoryIdOnAnAsyncThread() {
        IReservationService reservations = mock(IReservationService.class);
        when(reservations.queryMyReservations(1)).thenAnswer(call -> {
            assertEquals(42L, UserHolder.getUser().getId());
            return Result.ok(java.util.List.of(java.util.Map.of("id", 7L, "status", 0)));
        });
        String result = CompletableFuture.supplyAsync(() -> {
            String answer = new ReservationTool(reservations).queryMyReservations("chat:memory:42:test",1);
            assertNull(UserHolder.getUser());
            return answer;
        }).join();
        assertTrue(result.contains("\"success\":true"));
        verify(reservations).queryMyReservations(1);
    }
}
