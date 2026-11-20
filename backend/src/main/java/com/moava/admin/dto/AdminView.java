package com.moava.admin.dto;

public record AdminView(
        Long adminId,
        String name,
        String email,
        String status) {
}
