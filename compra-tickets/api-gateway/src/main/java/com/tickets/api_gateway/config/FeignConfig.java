package com.tickets.api_gateway.config;

import com.tickets.api_gateway.exception.FeignErrorDecoder;
import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración global de Feign.
 *
 * Al registrar FeignErrorDecoder como @Bean aquí (sin anotarlo como @Component
 * adicional), se aplica a TODOS los clientes Feign del contexto de forma limpia.
 */
@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Nivel de log para Feign. Se puede sobreescribir por cliente en properties:
     * feign.client.config.<nombre>.logger-level=FULL
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}