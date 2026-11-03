package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Reservation;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopManager;
import com.hmdp.entity.User;
import com.hmdp.mapper.ReservationMapper;
import com.hmdp.mapper.ShopManagerMapper;
import com.hmdp.service.IReservationService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl extends ServiceImpl<ReservationMapper, Reservation> implements IReservationService {
    private static final int PAGE_SIZE = 10;
    private final IUserService userService;
    private final IShopService shopService;
    private final ShopManagerMapper shopManagerMapper;

    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        return user == null ? null : user.getId();
    }

    @Override
    public Result createReservation(ReservationDTO dto) {
        Long userId = currentUserId();
        if (userId == null) return Result.fail("请先登录");
        if (dto == null || dto.getShopId() == null || dto.getShopId() <= 0
                || dto.getReservationTime() == null || dto.getNumPeople() == null) {
            return Result.fail("请填写店铺、预约时间和人数");
        }
        if (dto.getNumPeople() < 1 || dto.getNumPeople() > 20) return Result.fail("预约人数须为1至20人");
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        if (remark.length() > 255) return Result.fail("备注不能超过255字");
        LocalDateTime time = dto.getReservationTime().withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now();
        if (!time.isAfter(now)) return Result.fail("请选择未来的预约时间");
        if (time.isAfter(now.plusDays(90))) return Result.fail("最多可提前90天预约");
        if (shopService.getById(dto.getShopId()) == null) return Result.fail("店铺不存在");
        User user = userService.getById(userId);
        if (user == null) return Result.fail("用户不存在，请重新登录");

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setUserPhone(user.getPhone());
        reservation.setShopId(dto.getShopId());
        reservation.setReservationTime(time);
        reservation.setNumPeople(dto.getNumPeople());
        reservation.setRemark(remark);
        reservation.setStatus(0);
        try {
            return save(reservation) ? Result.ok(reservation.getId()) : Result.fail("预约提交失败，请重试");
        } catch (DuplicateKeyException e) {
            return Result.fail("这个时间已有预约，请勿重复提交");
        }
    }

    @Override
    public Result queryMyReservations(Integer current) {
        Long userId = currentUserId();
        if (userId == null) return Result.fail("请先登录");
        int pageNumber = current == null ? 1 : Math.max(1, Math.min(current, 10000));
        Page<Reservation> page = lambdaQuery().eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreateTime).orderByDesc(Reservation::getId)
                .page(new Page<>(pageNumber, PAGE_SIZE));
        List<Reservation> items = page.getRecords();
        List<Long> shopIds = items.stream().map(Reservation::getShopId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Shop> shops = shopIds.isEmpty() ? Collections.emptyMap() : shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        items.forEach(item -> setShopDetails(item, shops.get(item.getShopId())));
        return Result.ok(items, page.getTotal());
    }

    @Override
    public Result queryMyReservation(Long id) {
        Long userId = currentUserId();
        if (userId == null) return Result.fail("请先登录");
        Reservation item = id == null ? null : getById(id);
        if (item == null || !userId.equals(item.getUserId())) return Result.fail("预约不存在");
        setShopDetails(item, shopService.getById(item.getShopId()));
        return Result.ok(item);
    }

    @Override
    public Result cancelMyReservation(Long id) {
        Long userId = currentUserId();
        if (userId == null) return Result.fail("请先登录");
        if (id == null || id <= 0) return Result.fail("预约不存在");
        boolean changed = lambdaUpdate().eq(Reservation::getId, id).eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, 0, 1).set(Reservation::getStatus, 2)
                .set(Reservation::getUpdateTime, LocalDateTime.now()).update();
        return changed ? Result.ok() : Result.fail("预约不存在或已经取消");
    }

    @Override
    public Result queryManagedReservations(Integer current) {
        Long userId = currentUserId();
        if (userId == null) return Result.fail("请先登录");
        List<Long> shopIds = managedShopIds(userId);
        if (shopIds.isEmpty()) return Result.ok(Map.of("shops", List.of(), "records", List.of(), "total", 0));
        List<Shop> shopList = shopService.listByIds(shopIds);
        Map<Long, Shop> shops = shopList.stream().collect(Collectors.toMap(Shop::getId, s -> s));
        int pageNumber = current == null ? 1 : Math.max(1, Math.min(current, 10000));
        Page<Reservation> page = lambdaQuery().in(Reservation::getShopId, shopIds)
                .orderByDesc(Reservation::getCreateTime).orderByDesc(Reservation::getId)
                .page(new Page<>(pageNumber, PAGE_SIZE));
        page.getRecords().forEach(item -> setShopDetails(item, shops.get(item.getShopId())));
        return Result.ok(Map.of("shops", shopList, "records", page.getRecords(), "total", page.getTotal()));
    }

    @Override
    public Result decideManagedReservation(Long id, boolean confirm) {
        Long userId = currentUserId();
        if (userId == null) return Result.fail("请先登录");
        if (id == null || id <= 0) return Result.fail("预约不存在或无法处理");
        List<Long> shopIds = managedShopIds(userId);
        if (shopIds.isEmpty()) return Result.fail("没有该店铺的管理权限");
        var update = lambdaUpdate().eq(Reservation::getId, id).in(Reservation::getShopId, shopIds)
                .eq(Reservation::getStatus, 0);
        if (confirm) update.gt(Reservation::getReservationTime, LocalDateTime.now());
        boolean changed = update.set(Reservation::getStatus, confirm ? 1 : 3)
                .set(Reservation::getUpdateTime, LocalDateTime.now()).update();
        return changed ? Result.ok() : Result.fail("预约不存在、已处理或预约时间已过");
    }

    private List<Long> managedShopIds(Long userId) {
        return shopManagerMapper.selectList(new LambdaQueryWrapper<ShopManager>()
                        .eq(ShopManager::getUserId, userId))
                .stream().map(ShopManager::getShopId).distinct().toList();
    }

    private void setShopDetails(Reservation reservation, Shop shop) {
        if (shop != null) {
            reservation.setShopName(shop.getName());
            reservation.setShopAddress(shop.getAddress());
        }
    }
}
