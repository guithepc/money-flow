package com.pctheone.money_flow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

//---- Who may read the admin metrics. Driven by the env var app.admin-owner-ids (e.g. "1" or "1,4"),
//---- so promoting an admin is a config change, not a schema/role migration.
@Component
public class AdminAccess {

    private final Set<Integer> adminOwnerIds;

    public AdminAccess(@Value("${app.admin-owner-ids:}") String ids) {
        this.adminOwnerIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
    }

    public boolean isAdmin(Integer ownerId) {
        return ownerId != null && adminOwnerIds.contains(ownerId);
    }

    public void require(Integer ownerId) {
        if (!isAdmin(ownerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
    }
}
