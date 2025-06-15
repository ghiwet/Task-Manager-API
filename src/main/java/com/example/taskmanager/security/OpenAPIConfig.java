package com.example.taskmanager.security;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Manager API")
                        .description("API documentation for task management secured with Keycloak")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("keycloak", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl("http://localhost:8082/realms/myrealm/protocol/openid-connect/auth")
                                                .tokenUrl("http://localhost:8082/realms/myrealm/protocol/openid-connect/token")
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect scope")
                                                        .addString("profile", "User profile scope")
                                                        .addString("email", "Email access")
                                                        .addString("roles", "Access to user roles")
                                                )
                                        )
                                        .password(new OAuthFlow()
                                                .tokenUrl("http://localhost:8082/realms/myrealm/protocol/openid-connect/token")
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect scope")
                                                        .addString("profile", "User profile")
                                                        .addString("email", "User email")
                                                )
                                        )
                                )
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("keycloak", List.of("openid", "profile", "email", "roles")));
    }
}
