package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.config.AdminAccess;
import com.pctheone.money_flow.dto.AdminMetricsDTO;
import com.pctheone.money_flow.services.AdminMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminMetricsService metricsService;
    private final AdminAccess adminAccess;

    public AdminController(AdminMetricsService metricsService, AdminAccess adminAccess) {
        this.metricsService = metricsService;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/metrics")
    public ResponseEntity<AdminMetricsDTO> metrics() {
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        adminAccess.require(ownerId);
        log.info("Admin metrics requested by ownerId={}", ownerId);
        return ResponseEntity.ok(metricsService.collect());
    }
}
