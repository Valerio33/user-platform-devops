package com.aston.userservice.event;

/** JSON contract: operation is CREATED or DELETED; email is the recipient. */
public record UserEvent(Operation operation, String email) {
    public enum Operation { CREATED, DELETED }
}
