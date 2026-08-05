package org.example.traveljava.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 接口文档配置。
 *
 * 文档地址：
 *  - UI：   http://localhost:3200/swagger-ui.html（或 /swagger-ui/index.html）
 *  - JSON： http://localhost:3200/v3/api-docs
 *
 * 走 JWT 的接口点右上角「Authorize」填入 `Bearer <token>` 即可调试。
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI travelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 智能旅游助手 API")
                        .description("""
                                基于 Spring Boot 3.2 的旅游规划后端。
                                包含：AI 行程规划（SSE 流式）、用户社交、地图、订单/支付/酒店预订、
                                Agent 微服务透传、行程分享等接口。

                                ⚠️ 需要登录的接口请在右上角 Authorize 填入：`Bearer <JWT_TOKEN>`。
                                """)
                        .version("v4.2.0")
                        .contact(new Contact().name("AI Travel"))
                        .license(new io.swagger.v3.oas.models.info.License().name("MIT")))
                .tags(List.of(
                        new Tag().name("行程规划").description("AI 行程生成（SSE 流式 / 非流式）与行程管理"),
                        new Tag().name("AI 对话").description("多供应商 LLM 对话与推荐"),
                        new Tag().name("认证").description("注册 / 登录 / 个人资料"),
                        new Tag().name("社交").description("游记 / 帖子 / 评论 / 关注 / 收藏"),
                        new Tag().name("地图").description("百度 / 高德地图 POI、地理编码、热目的地"),
                        new Tag().name("电商").description("订单 / 支付 / 酒店 / 机票 / 优惠券"),
                        new Tag().name("分享").description("行程短链分享（公开只读快照）"),
                        new Tag().name("Agent").description("Python Agent 微服务透传（同步 + SSE）"),
                        new Tag().name("系统").description("健康检查 / 上传 / 图片代理 / 反馈")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后返回的 JWT Token，格式：Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
