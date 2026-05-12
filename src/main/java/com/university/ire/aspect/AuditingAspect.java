package com.university.ire.aspect;

import com.university.ire.entity.AuditLog;
import com.university.ire.repository.AuditLogRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditingAspect {

    private final AuditLogRepository auditLogRepository;

    public AuditingAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @AfterReturning("execution(* com.university.ire.controller..*(..))")
    public void auditControllerCall(JoinPoint joinPoint) {
        AuditLog log = new AuditLog();
        log.setAction(joinPoint.getSignature().toShortString());
        log.setDetails("Controller invoked");
        log.setActor("system");
        auditLogRepository.save(log);
    }
}
