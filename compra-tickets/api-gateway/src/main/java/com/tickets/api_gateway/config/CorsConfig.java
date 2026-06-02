package com.tickets.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de CORS para el API Gateway.
 *
 * Los orígenes permitidos se leen de application.properties (cors.allowed-origins),
 * por lo que no hay que tocar código para cambiar entre dev y producción.
 *
 * Ejemplo en properties:
 *   cors.allowed-origins=http://localhost:5173,http://localhost:3000
 */
@Configuration
public class CorsConfig {

    /**
     * Lista de orígenes separados por coma.
     * Ej.: "http://localhost:5173,https://mi-cine.com"
     */
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Orígenes: split por coma y trim de espacios
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList();
        config.setAllowedOrigins(origins);

        // Métodos HTTP que puede usar el frontend
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Headers que el frontend puede enviar
        config.setAllowedHeaders(List.of("*"));

        // Permitir cookies / credenciales si el frontend las necesita
        config.setAllowCredentials(true);

        // Cuánto tiempo el navegador puede cachear la respuesta preflight (segundos)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}