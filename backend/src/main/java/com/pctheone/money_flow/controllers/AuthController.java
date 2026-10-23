package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.LoginRequestDTO;
import com.pctheone.money_flow.dto.LoginResponseDTO;
import com.pctheone.money_flow.dto.PasswordResetConfirmDTO;
import com.pctheone.money_flow.dto.PasswordResetRequestDTO;
import com.pctheone.money_flow.dto.RegisterRequestDTO;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.exceptions.PasswordResetException;
import com.pctheone.money_flow.exceptions.PasswordResetRateLimitedException;
import com.pctheone.money_flow.exceptions.WeakPasswordException;
import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.OwnerService;
import com.pctheone.money_flow.services.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthService authService;

    @Autowired
    OwnerService ownerService;

    @Autowired
    PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login (@RequestBody LoginRequestDTO loginRequestDTO){
        log.info("Login requested.");
        Integer ownerId = authService.authenticate(loginRequestDTO.getEmail(), loginRequestDTO.getPassword()).orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        String token = authService.generateToken(ownerId);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponseDTO> register (@RequestBody RegisterRequestDTO registerRequestDTO){
        log.info("Register requested.");
        Integer ownerId = ownerService.registerOwner(registerRequestDTO.getName(), registerRequestDTO.getEmail(), registerRequestDTO.getPassword()).orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        String token = authService.generateToken(ownerId);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    //---- Always 202 with an empty body (no user enumeration); 429 when rate limited.
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset (@RequestBody PasswordResetRequestDTO request){
        log.info("Password reset requested.");
        if (request.getEmail() == null || request.getEmail().isBlank())
            return ResponseEntity.badRequest().build();
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset (@RequestBody PasswordResetConfirmDTO request){
        log.info("Password reset confirmation received.");
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<Map<String, String>> weakPassword (WeakPasswordException e){
        return ResponseEntity.badRequest().body(Map.of("error", WeakPasswordException.CODE));
    }

    @ExceptionHandler(PasswordResetException.class)
    public ResponseEntity<Map<String, String>> passwordResetRejected (PasswordResetException e){
        log.info("Password reset rejected: {}", e.getReason());
        return ResponseEntity.badRequest().body(Map.of("error", e.getReason().name()));
    }

    @ExceptionHandler(PasswordResetRateLimitedException.class)
    public ResponseEntity<Void> passwordResetRateLimited (PasswordResetRateLimitedException e){
        log.info("Password reset rate limited.");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
}
