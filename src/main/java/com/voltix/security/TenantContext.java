package com.voltix.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantContext {
    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenant(Long tenantId) {
        log.debug("Setting tenant context: {}", tenantId);
        currentTenant.set(tenantId);
    }

    public static Long getCurrentTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        currentTenant.remove();
    }
}
