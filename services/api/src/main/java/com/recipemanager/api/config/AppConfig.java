package com.recipemanager.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    // RestTemplate is Spring's synchronous HTTP client — used here to call Google's OAuth2
    // and userinfo endpoints. C# equivalent: new HttpClient() registered as a singleton
    // via services.AddHttpClient(). RestTemplate is thread-safe and should be shared.
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}