package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 超大王
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    private final IVoucherOrderService voucherOrderService;

    public VoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("/my")
    public Result myOrders(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return voucherOrderService.queryMyOrders(current);
    }

    @PostMapping("/claim/{id}")
    public Result claimVoucher(@PathVariable("id") Long id) {
        return voucherOrderService.claimVoucher(id);
    }

    @GetMapping("/result/{id}")
    public Result orderResult(@PathVariable("id") Long id) {
        return voucherOrderService.queryOrderResult(id);
    }
}
