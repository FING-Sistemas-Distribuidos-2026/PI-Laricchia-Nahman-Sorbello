package com.tickets.compra.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "system_parameters")
public class SystemParameter {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    public SystemParameter() {}

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
