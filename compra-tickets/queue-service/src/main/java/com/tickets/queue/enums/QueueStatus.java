package com.tickets.queue.enums;

/**
 * Estados posibles de un usuario en la cola.
 * Debe coincidir exactamente con com.tickets.compra.enums.QueueStatus de Aida.
 *
 * Flujo: WAITING → BUYING → PURCHASED | EXPIRED | CANCELLED
 */
public enum QueueStatus {
    WAITING,
    BUYING,
    PURCHASED,
    EXPIRED,
    CANCELLED
}