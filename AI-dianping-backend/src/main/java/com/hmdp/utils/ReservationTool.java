package com.hmdp.utils;
import cn.hutool.json.JSONUtil;
import com.hmdp.service.IReservationService;
import dev.langchain4j.agent.tool.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationTool {
    private final IReservationService reservations;
    @Tool("查询当前用户自己的一条预约详情")
    public String queryReservationById(@ToolMemoryId String memoryId, Long reservationId) {
        return JSONUtil.toJsonStr(ToolUserContext.call(memoryId, () -> reservations.queryMyReservation(reservationId)));
    }
    @Tool("分页查询当前用户自己的预约；current 从 1 开始")
    public String queryMyReservations(@ToolMemoryId String memoryId, Integer current) {
        return JSONUtil.toJsonStr(ToolUserContext.call(memoryId, () -> reservations.queryMyReservations(current)));
    }
}

