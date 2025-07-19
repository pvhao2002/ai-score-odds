package com.app.kira.aspect;


import com.app.kira.server.ServerInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Log
@Aspect
@Component
@RequiredArgsConstructor
public class ScheduleAOP {
    private final ServerInfoService serverInfoService;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object beforeSchedule(ProceedingJoinPoint joinPoint) throws Throwable {
        var methodName = joinPoint.getTarget().getClass().getPackageName() + "." + joinPoint.getSignature().getName();
        if (serverInfoService.isNotActive() || !serverInfoService.isScheduledMethodActive(methodName)) {
            return null;
        }
        return joinPoint.proceed();
    }
}
