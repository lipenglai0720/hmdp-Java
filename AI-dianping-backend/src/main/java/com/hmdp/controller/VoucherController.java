package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.ShopAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;


/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    private final IVoucherService voucherService;
    private final ShopAccess shopAccess;

    public VoucherController(IVoucherService voucherService, ShopAccess shopAccess) {
        this.voucherService = voucherService;
        this.shopAccess = shopAccess;
    }

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        if (voucher == null || !shopAccess.canManage(voucher.getShopId())) return Result.fail("没有该店铺的管理权限");
        String error = validateVoucher(voucher);
        if (error != null) return Result.fail(error);
        voucher.setId(null);
        voucher.setType(0);
        voucher.setStatus(1);
        if (!voucherService.save(voucher)) return Result.fail("优惠券保存失败");
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        if (voucher == null || !shopAccess.canManage(voucher.getShopId())) return Result.fail("没有该店铺的管理权限");
        String error = validateVoucher(voucher);
        if (error != null) return Result.fail(error);
        if (voucher.getStock() == null || voucher.getStock() < 1 || voucher.getStock() > 100000
                || voucher.getBeginTime() == null || voucher.getEndTime() == null
                || !voucher.getEndTime().isAfter(voucher.getBeginTime()) || !voucher.getEndTime().isAfter(LocalDateTime.now())) {
            return Result.fail("请填写有效的库存和活动时间");
        }
        voucher.setId(null);
        voucher.setType(1);
        voucher.setStatus(1);
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }

    private String validateVoucher(Voucher voucher) {
        if (voucher.getTitle() == null || voucher.getTitle().isBlank() || voucher.getTitle().length() > 255)
            return "请填写不超过255字的优惠标题";
        if (voucher.getSubTitle() != null && voucher.getSubTitle().length() > 255) return "优惠说明过长";
        if (voucher.getRules() != null && voucher.getRules().length() > 1024) return "使用规则过长";
        if (voucher.getPayValue() == null || voucher.getActualValue() == null
                || voucher.getPayValue() < 0 || voucher.getActualValue() <= 0
                || voucher.getPayValue() > voucher.getActualValue() || voucher.getActualValue() > 100_000_000L)
            return "请填写有效的价格和面值";
        voucher.setTitle(voucher.getTitle().trim());
        return null;
    }
}
