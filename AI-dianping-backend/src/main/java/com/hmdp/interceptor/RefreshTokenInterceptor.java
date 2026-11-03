package com.hmdp.interceptor;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.LoginSessionResolver;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final LoginSessionResolver sessions;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserHolder.removeUser();
        UserDTO user = sessions.resolve(request.getHeader("authorization"));
        if (user != null) UserHolder.saveUser(user);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}

