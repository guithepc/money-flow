package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.LoginRequestDTO;
import com.pctheone.money_flow.dto.LoginResponseDTO;
import com.pctheone.money_flow.dto.PasswordResetConfirmDTO;
import com.pctheone.money_flow.dto.PasswordResetRequestDTO;
import com.pctheone.money_flow.dto.RefreshRotationDTO;
import com.pctheone.money_flow.dto.RegisterRequestDTO;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.exceptions.PasswordResetException;
import com.pctheone.money_flow.exceptions.PasswordResetRateLimitedException;
import com.pctheone.money_flow.exceptions.WeakPasswordException;
import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.OwnerService;
import com.pctheone.money_flow.services.PasswordResetService;
import com.pctheone.money_flow.services.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    //---- The refresh cookie is only ever sent to /api/auth/*, never to the data endpoints.
    static final String REFRESH_COOKIE = "mf_refresh";
    static final String REFRESH_COOKIE_PATH = "/api/auth";

    @Autowired
    AuthService authService;

    @Autowired
    OwnerService ownerService;

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    RefreshTokenService refreshTokenService;

    //---- false only for local http dev; browsers drop Secure cookies set over plain http (except on localhost).
    @Value("${app.auth.cookie-secure:true}")
    boolean cookieSecure;

    @Value("${jwt.refresh-expiration-days:30}")
    long refreshExpirationDays;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    String[] allowedOrigins;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login (@RequestBody LoginRequestDTO loginRequestDTO, HttpServletRequest request){
        log.info("Login requested.");
        Integer ownerId = authService.authenticate(loginRequestDTO.getEmail(), loginRequestDTO.getPassword()).orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        return startSession(ownerId, request);
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponseDTO> register (@RequestBody RegisterRequestDTO registerRequestDTO, HttpServletRequest request){
        log.info("Register requested.");
        Integer ownerId = ownerService.registerOwner(registerRequestDTO.getName(), registerRequestDTO.getEmail(), registerRequestDTO.getPassword(), registerRequestDTO.getAcceptedTermsVersion()).orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        return startSession(ownerId, request);
    }

    //---- Exchanges the refresh cookie for a new access token and rotates the cookie.
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh (@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest request){
        if (!isTrustedOrigin(request)) {
            log.warn("Refresh refused: untrusted Origin {}", request.getHeader(HttpHeaders.ORIGIN));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<RefreshRotationDTO> rotation = refreshTokenService.rotate(refreshToken, request.getHeader(HttpHeaders.USER_AGENT));
        if (rotation.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                    .build();
        }
        String accessToken = authService.generateToken(rotation.get().getOwnerId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotation.get().getRefreshToken()).toString())
                .body(new LoginResponseDTO(accessToken));
    }

    //---- Revokes the session server-side and clears the cookie. Works with an expired access token.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout (@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest request){
        if (!isTrustedOrigin(request)) {
            log.warn("Logout refused: untrusted Origin {}", request.getHeader(HttpHeaders.ORIGIN));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        refreshTokenService.revokeSession(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<LoginResponseDTO> startSession(Integer ownerId, HttpServletRequest request) {
        String accessToken = authService.generateToken(ownerId);
        String refreshToken = refreshTokenService.issue(ownerId, request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
                .body(new LoginResponseDTO(accessToken));
    }

    private ResponseCookie refreshCookie(String value) {
        return baseCookie(value).maxAge(Duration.ofDays(refreshExpirationDays)).build();
    }

    private ResponseCookie clearRefreshCookie() {
        return clearRefreshCookie(cookieSecure);
    }

    //---- Also used by OwnerController: deleting the account ends the session in the browser too.
    static ResponseCookie clearRefreshCookie(boolean secure) {
        return baseCookie("", secure).maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return baseCookie(value, cookieSecure);
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String value, boolean secure) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH);
    }

    //---- CSRF defense on top of SameSite=Strict (other *.pctheone.com hosts are still "same site").
    //---- Requests without Origin (curl, tests) carry no browser cookie risk and are let through.
    boolean isTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (Arrays.stream(allowedOrigins).map(String::trim).anyMatch(origin::equalsIgnoreCase)) {
            return true;
        }
        //---- Same host as the API (prod serves the dashboard and /api from one host).
        try {
            String originHost = URI.create(origin).getHost();
            return originHost != null && originHost.equalsIgnoreCase(request.getServerName());
        } catch (IllegalArgumentException e) {
            return false;
        }
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
