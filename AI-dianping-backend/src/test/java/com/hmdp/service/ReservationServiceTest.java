package com.hmdp.service;

import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.ShopManagerMapper;
import com.hmdp.service.impl.ReservationServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private IUserService users;
    private IShopService shops;
    private ShopManagerMapper managers;
    private ReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(IUserService.class);
        shops = mock(IShopService.class);
        managers = mock(ShopManagerMapper.class);
        service = spy(new ReservationServiceImpl(users, shops, managers));
    }

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    private void signIn(long id) {
        UserDTO user = new UserDTO();
        user.setId(id);
        UserHolder.saveUser(user);
    }

    private ReservationDTO validRequest() {
        ReservationDTO dto = new ReservationDTO();
        dto.setShopId(4L);
        dto.setReservationTime(LocalDateTime.now().plusDays(1));
        dto.setNumPeople(2);
        return dto;
    }

    @Test
    void rejectsUnauthenticatedAndInvalidRequests() {
        assertFalse(service.createReservation(validRequest()).getSuccess());
        signIn(1);
        ReservationDTO dto = validRequest();
        dto.setNumPeople(21);
        assertFalse(service.createReservation(dto).getSuccess());
        dto.setNumPeople(2);
        dto.setReservationTime(LocalDateTime.now().minusMinutes(1));
        assertFalse(service.createReservation(dto).getSuccess());
        verifyNoInteractions(shops, users);
    }

    @Test
    void reportsConcurrentDuplicateAsAUsefulMessage() {
        signIn(1);
        when(shops.getById(4L)).thenReturn(new Shop().setId(4L));
        when(users.getById(1L)).thenReturn(new User().setId(1L).setPhone("13800000000"));
        doThrow(new DuplicateKeyException("duplicate")).when(service).save(any(Reservation.class));
        Result result = service.createReservation(validRequest());
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("已有预约"));
    }

    @Test
    void doesNotExposeAnotherUsersReservation() {
        signIn(1);
        Reservation someoneElses = new Reservation().setId(9L).setUserId(2L).setShopId(4L);
        doReturn(someoneElses).when(service).getById(9L);
        Result result = service.queryMyReservation(9L);
        assertFalse(result.getSuccess());
        assertNull(result.getData());
        verifyNoInteractions(shops);
    }

    @Test
    void managerCannotActWithoutAnAssignment() {
        signIn(1);
        when(managers.selectList(any())).thenReturn(List.of());
        Result result = service.decideManagedReservation(9L, true);
        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("管理权限"));
    }
}
