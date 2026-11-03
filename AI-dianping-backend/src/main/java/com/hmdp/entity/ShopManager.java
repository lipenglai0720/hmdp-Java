package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tb_shop_manager")
public class ShopManager {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
}
