package shop.payment.config;

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
            title = "Payment service API",
            description =
                "LiqPay payment form generation, payment/subscription callbacks and user"
                    + " subscription management",
            version = "1.0"),
    // JWT is the default; the two anonymous LiqPay callbacks opt out with an empty
    // @SecurityRequirements on the controller methods
    security = @SecurityRequirement(name = "bearer-jwt"))
@SecurityScheme(
    name = "bearer-jwt",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
