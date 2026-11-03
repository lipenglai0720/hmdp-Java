package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;

public interface IReservationService extends IService<Reservation> {
    Result createReservation(ReservationDTO dto);
    Result queryMyReservations(Integer current);
    Result queryMyReservation(Long id);
    Result cancelMyReservation(Long id);
    Result queryManagedReservations(Integer current);
    Result decideManagedReservation(Long id, boolean confirm);
}
