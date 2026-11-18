package com.moava.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/")
    public String home() {
        return "MOAVA Backend Server is Running!";
    }

    @GetMapping("/api/test")
    public String test() {
        return "API 연결 성공!";
    }
}