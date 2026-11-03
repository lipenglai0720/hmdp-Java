package com.hmdp.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class))
            .withUserConfiguration(McpServerConfig.class)
            .withPropertyValues("spring.jackson.default-property-inclusion=non_null");

    @Test
    void shouldWriteBlogResponseWithJavaTimeAndConfiguredJsonSettings() {
        contextRunner.run(context -> {
            LocalDateTime createTime = LocalDateTime.of(2026, 10, 23, 16, 30);
            LocalDateTime updateTime = createTime.plusMinutes(1);
            Blog blog = new Blog().setId(1L).setCreateTime(createTime).setUpdateTime(updateTime);
            MockHttpOutputMessage response = new MockHttpOutputMessage();

            context.getBean(MappingJackson2HttpMessageConverter.class)
                    .write(Result.ok(List.of(blog)), MediaType.APPLICATION_JSON, response);

            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonNode body = mapper.readTree(response.getBodyAsString(StandardCharsets.UTF_8));
            JsonNode blogJson = body.path("data").get(0);
            assertThat(body.path("success").asBoolean()).isTrue();
            assertThat(blogJson.path("createTime").asText()).isEqualTo("2026-10-23T16:30:00");
            assertThat(blogJson.path("updateTime").asText()).isEqualTo("2026-10-23T16:31:00");
            assertThat(body.has("errorMsg")).isFalse();
            assertThat(blogJson.has("title")).isFalse();

            Blog restoredBlog = mapper.treeToValue(blogJson, Blog.class);
            assertThat(restoredBlog.getCreateTime()).isEqualTo(createTime);
            assertThat(restoredBlog.getUpdateTime()).isEqualTo(updateTime);
        });
    }
}
