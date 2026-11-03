package com.hmdp.utils;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 工具在异步线程执行时，使用框架注入的服务端会话 ID 恢复身份。 */
public final class ToolUserContext {
    private static final Pattern MEMORY = Pattern.compile("chat:memory:([1-9][0-9]{0,18}):[A-Za-z0-9_-]{1,64}");
    private ToolUserContext() {}
    public static Result call(String memoryId, Supplier<Result> action) {
        Matcher matcher = MEMORY.matcher(memoryId == null ? "" : memoryId);
        if (!matcher.matches()) return Result.fail("请先登录后再查询个人业务");
        UserDTO user = new UserDTO();
        try { user.setId(Long.parseLong(matcher.group(1))); }
        catch (NumberFormatException e) { return Result.fail("会话无效"); }
        return asUser(user, action);
    }
    public static Result asUser(UserDTO user, Supplier<Result> action) {
        if (user == null || user.getId() == null) return Result.fail("请先登录后再调用此工具");
        UserDTO previous = UserHolder.getUser();
        try { UserHolder.saveUser(user); return action.get(); }
        finally {
            if (previous == null) UserHolder.removeUser();
            else UserHolder.saveUser(previous);
        }
    }
}
