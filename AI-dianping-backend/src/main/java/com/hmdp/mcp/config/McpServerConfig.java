package com.hmdp.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import com.hmdp.service.LoginSessionResolver;
import com.hmdp.dto.UserDTO;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;

@Configuration
public class McpServerConfig {

    public static final String MCP_ENDPOINT = "/mcp";

    @Bean
    public HttpServletStatelessServerTransport httpServletStatelessServerTransport(
            ObjectMapper objectMapper, ObjectProvider<LoginSessionResolver> sessions) {
        // 复用 Spring Boot 的 JSON 配置，保留 Java 时间类型支持。
        return HttpServletStatelessServerTransport.builder()
                .objectMapper(objectMapper)
                .contextExtractor(request -> {
                    LoginSessionResolver resolver = sessions.getIfAvailable();
                    UserDTO user = resolver == null ? null : resolver.resolve(request.getHeader("authorization"));
                    return user == null ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of("authenticatedUser", user));
                })
                .messageEndpoint(MCP_ENDPOINT)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServletRegistration(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> bean = new ServletRegistrationBean<>(transport);
        bean.addUrlMappings(MCP_ENDPOINT, MCP_ENDPOINT + "/");
        return bean;
    }
}
