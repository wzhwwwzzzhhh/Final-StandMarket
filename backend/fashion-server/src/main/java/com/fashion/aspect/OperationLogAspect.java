package com.fashion.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.context.BaseContext;
import com.fashion.entity.Employee;
import com.fashion.entity.OperationLog;
import com.fashion.mapper.EmployeeMapper;
import com.fashion.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 管理端操作日志切面：拦截标注了 @OperationLog 的 Controller 写操作方法，
 * 记录操作人、模块、操作、方法、参数(JSON)、IP、时间，落库可追溯。
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(operationLogAnnotation)")
    public Object handleOperationLog(ProceedingJoinPoint joinPoint, com.fashion.common.annotation.OperationLog operationLogAnnotation) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            saveLog(joinPoint, operationLogAnnotation);
            return result;
        } catch (Throwable e) {
            saveLog(joinPoint, operationLogAnnotation);
            throw e;
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, com.fashion.common.annotation.OperationLog operationLogAnnotation) {
        try {
            Long employeeId = BaseContext.getAdminId();
            if (employeeId == null) {
                return;
            }
            OperationLog entity = new OperationLog();
            entity.setEmployeeId(employeeId);
            entity.setEmployeeName(getEmployeeName(employeeId));
            entity.setModule(operationLogAnnotation.module());
            entity.setOperation(operationLogAnnotation.operation());
            entity.setMethod(buildMethod(joinPoint));
            entity.setParams(buildParams(joinPoint));
            entity.setIp(getIp());
            entity.setCreateTime(LocalDateTime.now());
            operationLogService.save(entity);
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", e.getMessage(), e);
        }
    }

    private String getEmployeeName(Long employeeId) {
        try {
            Employee employee = employeeMapper.getById(employeeId);
            return employee != null ? employee.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildMethod(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            return request.getMethod() + " " + request.getRequestURI();
        }
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName();
    }

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(\"([^\"]*(?:password|secret|token|credential)[^\"]*)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private String buildParams(ProceedingJoinPoint joinPoint) {
        List<Object> args = new ArrayList<>();
        for (Object arg : joinPoint.getArgs()) {
            if (isSkippable(arg)) {
                continue;
            }
            args.add(arg);
        }
        try {
            String json = objectMapper.writeValueAsString(args);
            return SENSITIVE_KEY_PATTERN.matcher(json).replaceAll("$1\"***\"");
        } catch (Exception e) {
            return args.toString();
        }
    }

    private boolean isSkippable(Object arg) {
        return arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof MultipartFile;
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}