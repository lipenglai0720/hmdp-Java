package com.hmdp.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AiSkillRunRequest {

    /** 兼容旧客户端；不作为身份依据，身份始终由登录会话决定。 */
    private Long userId;

    private Long merchantId;

    private Map<String, Object> input = new HashMap<>();
}
