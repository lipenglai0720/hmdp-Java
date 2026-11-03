package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.ShopAccess;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 超大王
 * @since 2025-09-18
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    public final IShopService shopService;
    private final ShopAccess shopAccess;

    public ShopController(IShopService shopService, ShopAccess shopAccess) {
        this.shopService = shopService;
        this.shopAccess = shopAccess;
    }

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    @GetMapping("/managed")
    public Result managedShops() {
        List<Long> ids = shopAccess.managedShopIds();
        return Result.ok(ids.isEmpty() ? List.of() : shopService.listByIds(ids));
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        return Result.fail("店铺创建需要管理员开通");
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        if (shop == null || !shopAccess.canManage(shop.getId())) return Result.fail("没有该店铺的管理权限");
        if ((shop.getName() != null && (shop.getName().isBlank() || shop.getName().length() > 128))
                || (shop.getAddress() != null && (shop.getAddress().isBlank() || shop.getAddress().length() > 255))
                || (shop.getArea() != null && shop.getArea().length() > 128)
                || (shop.getOpenHours() != null && shop.getOpenHours().length() > 32)
                || (shop.getImages() != null && shop.getImages().length() > 1024)
                || (shop.getAvgPrice() != null && (shop.getAvgPrice() < 0 || shop.getAvgPrice() > 1_000_000)))
            return Result.fail("店铺信息格式不正确");
        if ((shop.getX() == null) != (shop.getY() == null)
                || (shop.getX() != null && (!Double.isFinite(shop.getX()) || !Double.isFinite(shop.getY())
                || shop.getX() < -180 || shop.getX() > 180 || shop.getY() < -85.05112878 || shop.getY() > 85.05112878)))
            return Result.fail("店铺坐标格式不正确");
        Shop allowed = new Shop().setId(shop.getId()).setName(shop.getName()).setImages(shop.getImages())
                .setArea(shop.getArea()).setAddress(shop.getAddress()).setX(shop.getX()).setY(shop.getY())
                .setAvgPrice(shop.getAvgPrice()).setOpenHours(shop.getOpenHours());
        return shopService.updateShop(allowed);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return shopService.queryShopByType(typeId, current, x, y);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
