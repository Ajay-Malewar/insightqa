package com.ajaymalewar.insightqa.dto;

public class AuthDtos {

    public record RegisterRequest(String username, String password){
    }

    public record LoginRequest(String username, String password){
    }
    public record AuthResponse(String token){
    }
}
