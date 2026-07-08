package shop.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "liqpay")
public record LiqPayProperties(
        String publicKey,
        String privateKey
) {}
