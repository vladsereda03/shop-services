package shop.order.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Order service API",
            description =
                "User order checkout and history plus the internal order API used by"
                    + " payment-service callbacks",
            version = "1.0"),
    // every endpoint requires a JWT, so declare the requirement globally: Swagger UI
    // gets the Authorize button and each operation shows the padlock
    security = @SecurityRequirement(name = "bearer-jwt"))
@SecurityScheme(
    name = "bearer-jwt",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
