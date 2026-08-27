package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.LoginRequestDTO;
import com.pctheone.money_flow.dto.LoginResponseDTO;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login (@RequestBody LoginRequestDTO loginRequestDTO){
        log.info("Login requested.");
        Integer ownerId = authService.authenticate(loginRequestDTO.getEmail(), loginRequestDTO.getPassword()).orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        String token = authService.generateToken(ownerId);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }
}
