package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AiSkillRunRequest;
import com.hmdp.dto.AiSkillRunResponse;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AiSkill;
import com.hmdp.entity.AiSkillRun;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.AiSkillMapper;
import com.hmdp.mapper.AiSkillRunMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.IAiSkillService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiSkillServiceImpl extends ServiceImpl<AiSkillMapper, AiSkill> implements IAiSkillService {

    private static final String RUN_STATUS_SUCCESS = "SUCCESS";
    private static final String RUN_STATUS_FAILED = "FAILED";
    private static final int USER_MINUTE_LIMIT = 20;
    private static final DefaultRedisScript<Long> RATE_LIMIT = new DefaultRedisScript<>();
    static {
        RATE_LIMIT.setLocation(new ClassPathResource("ai_rate_limit.lua"));
        RATE_LIMIT.setResultType(Long.class);
    }

    private final ObjectProvider<OpenAiChatModel> chatModelProvider;

    @Resource
    private AiSkillRunMapper aiSkillRunMapper;

    @Resource
    private IShopService shopService;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public AiSkillServiceImpl(ObjectProvider<OpenAiChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public Result queryEnabledSkills() {
        List<AiSkill> skills = queryDbSkills();
        if (CollUtil.isEmpty(skills)) {
            skills = builtInSkills();
        }
        return Result.ok(skills.stream()
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .map(skill -> skill.setPromptTemplate(null))
                .sorted((a, b) -> Integer.compare(defaultSort(a), defaultSort(b)))
                .collect(Collectors.toList()));
    }

    @Override
    public Result querySkillByCode(String code) {
        AiSkill skill = findSkill(code);
        if (skill == null || !Boolean.TRUE.equals(skill.getEnabled())) {
            return Result.fail("AI技能不存在或已停用");
        }
        return Result.ok(skill.setPromptTemplate(null));
    }

    @Override
    public Result runSkill(String code, AiSkillRunRequest request) {
        AiSkill skill = findSkill(code);
        if (skill == null || !Boolean.TRUE.equals(skill.getEnabled())) {
            return Result.fail("AI技能不存在或已停用");
        }
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录后再使用AI技能");
        }
        Map<String, Object> input = request == null || request.getInput() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getInput());
        if (input.isEmpty() || input.size() > 30 || JSONUtil.toJsonStr(input).length() > 20000
                || input.entrySet().stream().anyMatch(e -> !e.getKey().matches("[A-Za-z0-9_]{1,64}")
                || (e.getValue() != null && !(e.getValue() instanceof String) && !(e.getValue() instanceof Number) && !(e.getValue() instanceof Boolean))
                || value(e.getValue()).length() > 10000)
                || input.values().stream().noneMatch(v -> !value(v).isBlank())) {
            return Result.fail("请填写有效的创作信息，总长度不超过20000字");
        }
        if (StrUtil.isBlank(skill.getPromptTemplate())) return Result.fail("技能模板未配置，请联系管理员");
        if (!allowRun(user.getId())) return Result.fail("AI技能调用太频繁，请稍后再试");
        Long merchantId = null; // 旧请求中的 merchantId/userId 均不作为授权或归属依据。
        String prompt = renderPrompt(skill, input);
        boolean fallback = false;
        String content;
        try {
            content = invokeModel(prompt);
            if (StrUtil.isBlank(content)) {
                fallback = true;
                content = fallbackContent(skill, input);
            }
        } catch (Exception e) {
            fallback = true;
            log.warn("AI技能调用失败，使用本地兜底结果。skillCode={}", code, e);
            content = fallbackContent(skill, input);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("skillCode", skill.getCode());
        output.put("skillName", skill.getName());
        output.put("content", content);
        output.put("fallback", fallback);
        output.put("generatedAt", LocalDateTime.now().toString());

        AiSkillRun run = new AiSkillRun()
                .setSkillCode(skill.getCode())
                .setUserId(user.getId())
                .setMerchantId(merchantId)
                .setInputJson(JSONUtil.toJsonStr(input))
                .setOutputJson(JSONUtil.toJsonStr(output))
                .setStatus(RUN_STATUS_SUCCESS)
                .setCreateTime(LocalDateTime.now());
        saveRunQuietly(run);

        AiSkillRunResponse response = new AiSkillRunResponse()
                .setRunId(run.getId())
                .setSkillCode(skill.getCode())
                .setSkillName(skill.getName())
                .setContent(content)
                .setOutput(output)
                .setFallback(fallback)
                .setHistorySaved(run.getId() != null);
        return Result.ok(response);
    }

    @Override
    public Result queryMyRuns(String skillCode) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录后再查看AI技能记录");
        }
        try {
            QueryWrapper<AiSkillRun> wrapper = new QueryWrapper<AiSkillRun>()
                    .eq("user_id", user.getId())
                    .orderByDesc("create_time")
                    .last("LIMIT 20");
            if (StrUtil.isNotBlank(skillCode)) {
                wrapper.eq("skill_code", skillCode);
            }
            return Result.ok(aiSkillRunMapper.selectList(wrapper));
        } catch (Exception e) {
            log.warn("查询AI技能记录失败，可能还未执行db.sql新增表", e);
            return Result.fail("创作记录暂时无法加载，请稍后重试");
        }
    }

    private List<AiSkill> queryDbSkills() {
        try {
            return query()
                    .orderByAsc("sort")
                    .list();
        } catch (Exception e) {
            log.warn("查询AI技能表失败，使用内置技能兜底", e);
            return new ArrayList<>();
        }
    }

    private AiSkill findSkill(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        try {
            AiSkill skill = query().eq("code", code).one();
            if (skill != null) {
                return skill;
            }
        } catch (Exception e) {
            log.warn("查询AI技能详情失败，尝试使用内置技能。code={}", code, e);
        }
        return builtInSkills().stream()
                .filter(skill -> Objects.equals(skill.getCode(), code))
                .findFirst()
                .orElse(null);
    }

    private boolean allowRun(Long userId) {
        String key = "ai:skill:limit:" + userId;
        Long count = stringRedisTemplate.execute(RATE_LIMIT, List.of(key));
        return count != null && count <= USER_MINUTE_LIMIT;
    }

    private String renderPrompt(AiSkill skill, Map<String, Object> input) {
        String prompt = skill.getPromptTemplate();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            prompt = prompt.replace("${" + entry.getKey() + "}", value(entry.getValue()));
        }
        prompt = prompt.replaceAll("\\$\\{[^}]+}", "【待补充】");
        return prompt + "\n\n业务上下文：\n" + buildBusinessContext(input)
                + "\n\n用户输入JSON：\n" + JSONUtil.toJsonStr(input)
                + "\n\n输出要求：只输出本次技能结果，不要安排下一步动作，不要声称已经替用户发布、预约或修改任何业务数据。";
    }

    private String buildBusinessContext(Map<String, Object> input) {
        StringBuilder builder = new StringBuilder();
        Long shopId = toLong(input.get("shopId"));
        if (shopId != null) {
            try {
                Shop shop = shopService.getById(shopId);
                if (shop != null) {
                    builder.append("店铺：")
                            .append(shop.getName())
                            .append("，商圈：")
                            .append(value(shop.getArea()))
                            .append("，地址：")
                            .append(value(shop.getAddress()))
                            .append("，评分：")
                            .append(shop.getScore() == null ? "未知" : shop.getScore() / 10.0)
                            .append("，人均：")
                            .append(shop.getAvgPrice() == null ? "未知" : shop.getAvgPrice())
                            .append("元。\n");
                    List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);
                    if (CollUtil.isNotEmpty(vouchers)) {
                        builder.append("可用优惠券：");
                        vouchers.stream().limit(3).forEach(v -> builder
                                .append(v.getTitle())
                                .append("/")
                                .append(v.getSubTitle())
                                .append("；"));
                        builder.append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("构建店铺业务上下文失败，继续使用用户输入。shopId={}", shopId, e);
            }
        }
        if (builder.length() == 0) {
            builder.append("暂无额外业务上下文，仅使用用户输入。");
        }
        return builder.toString();
    }

    private String invokeModel(String prompt) throws Exception {
        OpenAiChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return null;
        }
        Object result = tryInvoke(chatModel, "chat", prompt);
        if (result == null) {
            result = tryInvoke(chatModel, "generate", prompt);
        }
        return extractText(result);
    }

    private Object tryInvoke(Object target, String methodName, String prompt) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            return method.invoke(target, prompt);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("调用模型方法失败: " + methodName, e);
        }
    }

    private String extractText(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof String text) {
            return text;
        }
        Object content = invokeNoArg(result, "content");
        if (content instanceof String text) {
            return text;
        }
        Object aiMessage = invokeNoArg(result, "aiMessage");
        Object text = invokeNoArg(aiMessage, "text");
        if (text instanceof String messageText) {
            return messageText;
        }
        return result.toString();
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveRunQuietly(AiSkillRun run) {
        try {
            aiSkillRunMapper.insert(run);
        } catch (Exception e) {
            run.setStatus(RUN_STATUS_FAILED);
            run.setErrorMsg("AI技能记录表未初始化或写入失败");
            log.warn("保存AI技能运行记录失败", e);
        }
    }

    private String fallbackContent(AiSkill skill, Map<String, Object> input) {
        String outline = switch (skill.getCode()) {
            case "blog_draft" -> "标题：【店名 + 真实体验亮点】\n正文：【到店时间、体验项目、环境与服务】\n推荐理由：【基于亲身体验填写】\n提醒：【真实的人均消费与注意事项】";
            case "review_summary" -> "评价来源：【填写实际评价】\n共同优点：【列出有原文支持的观点】\n主要问题：【列出有原文支持的反馈】\n建议：【对应问题提出可执行建议】";
            case "review_reply" -> "您好，感谢您的反馈。关于【评价中的具体问题】，我们会【填写可以实际做到的处理方式】。感谢您帮助我们改善体验。";
            case "coupon_copy" -> "优惠标题：【店名 + 真实优惠】\n推广文案：【优惠内容与适用人群】\n使用条件：【有效时段、门槛与限制，以已发布券规则为准】";
            case "short_video_script" -> "0–3秒：【门头或真实体验亮点】\n3–15秒：【产品或项目实拍 + 核实后的介绍】\n15–25秒：【环境与细节】\n结尾：【真实地址、营业时间及到店提醒】";
            case "shop_tags" -> "候选标签：【根据已提供的描述逐一提取，并核对真实情况】";
            case "merchant_daily_report" -> "今日数据：【填入实际数据与口径】\n表现与问题：【结合数据说明】\n后续动作：【责任人与执行时间】";
            default -> "目标：【填写具体目标】\n依据：【填写已核实的信息】\n方案：【填写可执行内容】\n检查：【核对事实与使用条件】";
        };
        return "【本地编辑模板 · 模型暂不可用】\n以下为待填写的结构，未进行模型分析，也未核实商家事实。\n\n"
                + "已提供的信息：\n" + JSONUtil.toJsonPrettyStr(input) + "\n\n" + outline;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private int defaultSort(AiSkill skill) {
        return skill.getSort() == null ? 0 : skill.getSort();
    }

    private List<AiSkill> builtInSkills() {
        List<AiSkill> skills = new ArrayList<>();
        skills.add(skill("blog_draft", "AI写探店笔记", "consumer", 10,
                "根据店铺和关键词生成可编辑的探店笔记草稿。",
                "{\"shopId\":\"店铺ID，可选\",\"shopName\":\"店铺名称\",\"keywords\":\"体验关键词\",\"tone\":\"文风\"}",
                "你是本地生活内容编辑。请为${shopName}写一篇可编辑的探店笔记草稿，文风为${tone}，重点包含${keywords}。要求有标题、正文、推荐理由和避坑提醒。",
                "{\"title\":\"标题\",\"content\":\"正文\",\"tips\":\"提醒\"}"));
        skills.add(skill("review_summary", "AI总结店铺评价", "merchant", 20,
                "将评价内容总结成优点、问题和适合人群。",
                "{\"shopId\":\"店铺ID，可选\",\"shopName\":\"店铺名称\",\"keywords\":\"评价关键词或原文\"}",
                "你是商家运营助理。请基于${shopName}的评价信息，总结优点、待优化问题、适合人群和运营建议。评价信息：${keywords}。",
                "{\"advantages\":[],\"problems\":[],\"audience\":[],\"suggestions\":[]}"));
        skills.add(skill("review_reply", "AI生成评价回复", "merchant", 30,
                "为好评或差评生成礼貌、可直接编辑的商家回复。",
                "{\"shopName\":\"店铺名称\",\"review\":\"用户评价\",\"attitude\":\"回复语气\"}",
                "你是商家客服。请针对${shopName}的这条评价生成回复，语气为${attitude}。评价：${review}。不要承诺无法确认的赔偿或事实。",
                "{\"reply\":\"回复内容\"}"));
        skills.add(skill("coupon_copy", "AI生成优惠券文案", "merchant", 40,
                "生成优惠券标题、副标题和活动推广话术。",
                "{\"shopName\":\"店铺名称\",\"discount\":\"优惠力度\",\"target\":\"目标人群\",\"keywords\":\"卖点\"}",
                "你是本地生活营销策划。请为${shopName}生成优惠券标题、副标题和推广话术。优惠：${discount}，目标人群：${target}，卖点：${keywords}。",
                "{\"title\":\"券标题\",\"subtitle\":\"副标题\",\"copy\":\"推广话术\"}"));
        skills.add(skill("shop_tags", "AI提取店铺标签", "merchant", 50,
                "根据店铺信息提炼用于推荐和搜索的标签。",
                "{\"shopName\":\"店铺名称\",\"keywords\":\"店铺描述、评价或卖点\"}",
                "请从${shopName}的描述中提取8个以内短标签，用于本地生活推荐和搜索。描述：${keywords}。",
                "{\"tags\":[]}"));
        skills.add(skill("merchant_daily_report", "AI生成经营日报", "merchant", 60,
                "把商家经营数据转换成日报摘要。",
                "{\"shopName\":\"店铺名称\",\"keywords\":\"今日数据或观察\"}",
                "你是商家经营分析师。请为${shopName}生成一份简短经营日报，包含今日表现、问题提醒和明日动作建议。数据：${keywords}。",
                "{\"summary\":\"摘要\",\"risks\":[],\"actions\":[]}"));
        skills.add(skill("short_video_script", "AI生成短视频脚本", "merchant", 70,
                "生成本地生活探店短视频脚本。",
                "{\"shopName\":\"店铺名称\",\"keywords\":\"卖点\",\"duration\":\"视频时长\"}",
                "请为${shopName}生成一个${duration}短视频探店脚本，卖点：${keywords}。输出分镜、口播和结尾引导。",
                "{\"shots\":[],\"voiceover\":\"口播\",\"ending\":\"结尾\"}"));
        skills.add(skill("promotion_plan", "AI生成活动方案草稿", "merchant", 80,
                "生成可由商家确认后执行的活动方案草稿。",
                "{\"shopName\":\"店铺名称\",\"goal\":\"活动目标\",\"budget\":\"预算\",\"keywords\":\"主推产品或卖点\"}",
                "你是本地生活活动策划。请为${shopName}生成活动方案草稿。目标：${goal}，预算：${budget}，主推：${keywords}。只给草稿，不要声称已执行。",
                "{\"theme\":\"活动主题\",\"steps\":[],\"copy\":\"宣传文案\"}"));
        return skills;
    }

    private AiSkill skill(String code, String name, String scene, int sort, String description,
                          String inputSchema, String promptTemplate, String outputSchema) {
        return new AiSkill()
                .setCode(code)
                .setName(name)
                .setScene(scene)
                .setSort(sort)
                .setDescription(description)
                .setInputSchema(inputSchema)
                .setPromptTemplate(promptTemplate)
                .setOutputSchema(outputSchema)
                .setEnabled(true);
    }
}
