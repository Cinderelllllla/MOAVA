package com.moava.member.dto;

public record PasswordChangeRequest(String currentPassword, String newPassword) {
}