package com.hmdp.tools;
import cn.hutool.json.JSONUtil;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.ToolUserContext;
import dev.langchain4j.agent.tool.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderTool {
    private final IVoucherOrderService orders;
    @Tool("分页查询当前用户自己的优惠券订单，包含店铺、券名和真实订单状态；current 从 1 开始")
    public String queryMyOrders(@ToolMemoryId String memoryId, Integer current) {
        return JSONUtil.toJsonStr(ToolUserContext.call(memoryId, () -> orders.queryMyOrders(current)));
    }
}

