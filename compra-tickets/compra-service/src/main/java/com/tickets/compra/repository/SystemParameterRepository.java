package com.tickets.compra.repository;

import com.tickets.compra.entity.SystemParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SystemParameterRepository extends JpaRepository<SystemParameter, String> {
    Optional<SystemParameter> findByKey(String key);
}
