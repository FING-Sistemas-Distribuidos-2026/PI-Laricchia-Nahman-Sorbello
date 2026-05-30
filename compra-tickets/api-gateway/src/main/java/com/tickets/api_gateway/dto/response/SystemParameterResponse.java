package com.tickets.api_gateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response de GET /api/params/{key}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemParameterResponse {

    private String key;
    private String value;
}