package com.hmdp.mcp.config;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.*;
import com.hmdp.service.*;
import com.hmdp.utils.ToolUserContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import java.util.*;
import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
public class McpToolsConfig {
    private final IShopService shops;
    private final IShopTypeService types;
    private final IVoucherService vouchers;
    private final IVoucherOrderService orders;
    private final IReservationService reservations;
    private final IAiSkillService skills;

    @Bean
    public McpStatelessSyncServer mcpStatelessSyncServer(HttpServletStatelessServerTransport transport) {
        var server = McpServer.sync(transport).serverInfo("ai-dianping-mcp","2.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build()).build();
        register(server,"queryShopById","按店铺 ID 查询详情", Map.of("shopId","integer"),"shopId",false,
                a -> shops.queryById(number(a,"shopId",true).longValue()));
        register(server,"queryShopTypes","查询店铺分类",Map.of(),"",false,
                a -> types.queryShopType());
        register(server,"queryNearbyShops","按分类和经纬度查询附近店铺，按距离升序",
                Map.of("typeId","integer","current","integer","x","number","y","number"),"typeId,x,y",false,
                a -> shops.queryShopByType(number(a,"typeId",true).intValue(),page(a),
                        number(a,"x",true).doubleValue(),number(a,"y",true).doubleValue()));
        register(server,"searchShopByName","按名称搜索店铺，最多返回20条",Map.of("keyword","string"),"keyword",false,
                a -> Result.ok(shops.query().like("name",text(a,"keyword",100)).last("LIMIT 20").list()));
        register(server,"queryVouchersByShopId","查询店铺的普通及限时优惠券",Map.of("shopId","integer"),"shopId",false,
                a -> vouchers.queryVoucherOfShop(number(a,"shopId",true).longValue()));
        register(server,"queryEnabledAiSkills","查询已启用技能及输入字段",Map.of(),"",false,
                a -> skills.queryEnabledSkills());
        register(server,"queryMyOrders","查询登录用户自己的订单",Map.of("current","integer"),"",true,
                a -> orders.queryMyOrders(page(a)));
        register(server,"queryMyReservations","查询登录用户自己的预约",Map.of("current","integer"),"",true,
                a -> reservations.queryMyReservations(page(a)));
        register(server,"queryReservationById","查询当前用户自己的预约详情",Map.of("reservationId","integer"),"reservationId",true,
                a -> reservations.queryMyReservation(number(a,"reservationId",true).longValue()));
        register(server,"invokeAiSkill","运行技能，消耗当前用户额度并保存记录，返回可编辑草稿",
                Map.of("skillCode","string","input","object"),"skillCode,input",true,
                a -> {
                    if (!(a.get("input") instanceof Map<?,?> values)) throw new IllegalArgumentException("input 必须是对象");
                    Map<String,Object> input = new LinkedHashMap<>();
                    values.forEach((k,v) -> input.put(String.valueOf(k),v));
                    AiSkillRunRequest request = new AiSkillRunRequest();
                    request.setInput(input);
                    return skills.runSkill(text(a,"skillCode",64),request);
                });
        return server;
    }

    private void register(McpStatelessSyncServer server, String name, String description,
                          Map<String,String> fields, String required, boolean authenticated,
                          Function<Map<String,Object>,Result> handler) {
        Map<String,Object> properties = new LinkedHashMap<>();
        fields.forEach((k,v) -> properties.put(k,Map.of("type",v)));
        String schema = JSONUtil.toJsonStr(Map.of("type","object","properties",properties,
                "required",required.isEmpty()?List.of():Arrays.asList(required.split(",")),"additionalProperties",false));
        server.addTool(new McpStatelessServerFeatures.SyncToolSpecification(new McpSchema.Tool(name,description,schema),
                (context,request) -> {
                    Result result;
                    try {
                        Map<String,Object> args = request.arguments() == null ? Map.of() : request.arguments();
                        if (!fields.keySet().containsAll(args.keySet())) throw new IllegalArgumentException("存在不支持的参数");
                        result = authenticated
                                ? ToolUserContext.asUser((UserDTO)context.get("authenticatedUser"),() -> handler.apply(args))
                                : handler.apply(args);
                    } catch (IllegalArgumentException e) { result=Result.fail(e.getMessage()); }
                    catch (Exception e) { result=Result.fail("工具暂时不可用，请稍后重试"); }
                    return new McpSchema.CallToolResult(JSONUtil.toJsonStr(result),!Boolean.TRUE.equals(result.getSuccess()));
                }));
    }
    private Number number(Map<String,Object> args,String key,boolean required) {
        Object value=args.get(key);
        if(value==null&&!required) return null;
        if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException(key+" 必须是有效数字");
        if(!key.equals("x")&&!key.equals("y")&&(n.doubleValue()!=n.longValue()||n.longValue()<=0||n.longValue()>Integer.MAX_VALUE&&key.equals("typeId")))
            throw new IllegalArgumentException(key+" 必须是有效正整数");
        return n;
    }
    private int page(Map<String,Object> args) {
        Number n=number(args,"current",false);
        if(n!=null&&n.longValue()>10000) throw new IllegalArgumentException("页码过大");
        return n==null?1:n.intValue();
    }
    private String text(Map<String,Object> args,String key,int limit) {
        if(!(args.get(key) instanceof String s)||s.isBlank()||s.length()>limit)
            throw new IllegalArgumentException(key+" 不能为空且长度不能超过 "+limit);
        return s.trim();
    }
}

