package com.hmdp.tools;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.*;
import com.hmdp.service.IAiSkillService;
import com.hmdp.utils.ToolUserContext;
import dev.langchain4j.agent.tool.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiSkillTool {
    private final IAiSkillService skills;
    @Tool("查询已启用的 AI 技能及输入字段")
    public String queryEnabledAiSkills() { return JSONUtil.toJsonStr(skills.queryEnabledSkills()); }
    @Tool("根据技能编码及 input 对象生成内容；先查询技能输入字段，补齐用户提供的信息后调用。结果是草稿，不会发布或修改业务数据。")
    public String invokeAiSkill(@ToolMemoryId String memoryId, String skillCode, Map<String,Object> input) {
        if (skillCode == null || skillCode.isBlank() || input == null || input.isEmpty())
            return JSONUtil.toJsonStr(Result.fail("请提供技能编码及输入信息"));
        AiSkillRunRequest request = new AiSkillRunRequest();
        request.setInput(input);
        return JSONUtil.toJsonStr(ToolUserContext.call(memoryId, () -> skills.runSkill(skillCode.trim(),request)));
    }
}

