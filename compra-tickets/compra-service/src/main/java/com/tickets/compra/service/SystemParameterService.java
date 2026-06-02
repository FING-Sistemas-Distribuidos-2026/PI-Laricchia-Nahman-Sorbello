package com.tickets.compra.service;

import com.tickets.compra.entity.SystemParameter;
import com.tickets.compra.repository.SystemParameterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SystemParameterService {

    private final SystemParameterRepository systemParameterRepository;

    public SystemParameterService(SystemParameterRepository systemParameterRepository) {
        this.systemParameterRepository = systemParameterRepository;
    }

    /**
     * Devuelve el valor de un parámetro por clave.
     * Lanza 404 si la clave no existe — el caller no debe asumir defaults.
     */
    public SystemParameter getByKey(String key) {
        return systemParameterRepository.findById(key)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parámetro no encontrado: " + key));
    }
}