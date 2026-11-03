package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class LoginInterceptorTest {
    private final LoginInterceptor interceptor = new LoginInterceptor();

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void permitsPublicReadsButRequiresLoginForWritesAndReservations() throws Exception {
        assertTrue(allowed("GET", "/shop/of/type"));
        assertTrue(allowed("GET", "/voucher/list/1"));
        assertTrue(allowed("GET", "/blog/hot"));
        assertTrue(allowed("GET", "/blog/of/shop"));
        assertFalse(allowed("POST", "/blog/of/shop"));
        assertFalse(allowed("PUT", "/shop"));
        assertFalse(allowed("POST", "/voucher"));
        assertFalse(allowed("POST", "/upload/blog"));
        assertFalse(allowed("GET", "/reservation/my"));
        assertFalse(allowed("GET", "/voucher-order/my"));
        assertFalse(allowed("GET", "/shop/managed"));
        assertFalse(allowed("POST", "/blog-comments"));
    }

    private boolean allowed(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }
}
