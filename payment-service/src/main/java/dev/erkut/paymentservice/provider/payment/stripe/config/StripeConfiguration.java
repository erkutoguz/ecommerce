package dev.erkut.paymentservice.provider.payment.stripe.config;

import com.stripe.StripeClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfiguration {

    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        return new StripeClient(properties.secretKey());
    }
}