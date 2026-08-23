package com.ikdev.customersupportrouter.chatservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for the chat-service REST API.
 *
 * <p>Enables cross-origin requests from explicitly configured frontend origins,
 * allowing the browser to fetch from /messages, /conversations, and related endpoints.
 * Origins NOT in the allow-list will be rejected by the browser's CORS policy.
 * The allowed origins are externally configurable via the {@code app.cors.allowed-origins}
 * property; defaults to the local frontend dev server ({@code http://localhost:5173}).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        for (String path : new String[] { "/messages/**", "/conversations/**", "/tickets/**" }) {
            registry.addMapping(path)
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST")
                    .allowedHeaders("*")
                    .allowCredentials(false);
        }
    }
}
