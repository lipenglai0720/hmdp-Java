package com.hmdp.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VoucherOrderView {
    private String id;
    private Long voucherId;
    private Long shopId;
    private String shopName;
    private String voucherTitle;
    private Long payValue;
    private Integer status;
    private LocalDateTime createTime;
}
