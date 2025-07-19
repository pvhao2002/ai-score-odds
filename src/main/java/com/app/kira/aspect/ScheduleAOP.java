package com.app.kira.aspect;


import lombok.extern.java.Log;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Log
public class ScheduleAOP {
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object beforeSchedule(ProceedingJoinPoint joinPoint) {
        return null;
    }

}
