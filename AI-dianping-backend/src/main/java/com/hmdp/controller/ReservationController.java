package com.hmdp.controller;

import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {
    private final IReservationService reservationService;

    @PostMapping
    public Result create(@RequestBody ReservationDTO dto) {
        return reservationService.createReservation(dto);
    }

    @GetMapping("/my")
    public Result myList(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reservationService.queryMyReservations(current);
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        return reservationService.queryMyReservation(id);
    }

    @PutMapping("/{id}/cancel")
    public Result cancel(@PathVariable Long id) {
        return reservationService.cancelMyReservation(id);
    }

    @GetMapping("/managed")
    public Result managedList(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reservationService.queryManagedReservations(current);
    }

    @PutMapping("/{id}/confirm")
    public Result confirm(@PathVariable Long id) {
        return reservationService.decideManagedReservation(id, true);
    }

    @PutMapping("/{id}/reject")
    public Result reject(@PathVariable Long id) {
        return reservationService.decideManagedReservation(id, false);
    }
}
