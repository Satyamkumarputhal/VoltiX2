package com.voltix.security;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantSecurityAspect {

    @AfterReturning(pointcut = "execution(* com.voltix..*Repository.find*(..))", returning = "result")
    public void enforceTenantScoping(Object result) {
        Long currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            // No tenant context set (e.g. anonymous/system tasks), skip check
            return;
        }

        if (result instanceof java.util.Optional<?>) {
            java.util.Optional<?> optional = (java.util.Optional<?>) result;
            if (optional.isPresent()) {
                checkTenant(optional.get(), currentTenant);
            }
        } else if (result instanceof Iterable<?>) {
            for (Object obj : (Iterable<?>) result) {
                checkTenant(obj, currentTenant);
            }
        } else if (result != null) {
            checkTenant(result, currentTenant);
        }
    }

    private void checkTenant(Object entity, Long expectedTenantId) {
        Long entityTenantId = null;
        try {
            java.lang.reflect.Method getTenantIdMethod = entity.getClass().getMethod("getTenantId");
            entityTenantId = (Long) getTenantIdMethod.invoke(entity);
        } catch (NoSuchMethodException e) {
            // Entity has no tenant isolation requirements, skip
            return;
        } catch (Exception e) {
            // Ignore other reflection invocation exceptions
            return;
        }

        if (entityTenantId != null && !entityTenantId.equals(expectedTenantId)) {
            throw new AccessDeniedException("Access denied: cross-tenant data leakage detected.");
        }
    }
}
