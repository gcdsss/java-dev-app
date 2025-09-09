package com.gui.app.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

@Aspect
@Component
public class HttpLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger("HTTP_REQUEST_LOG");

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@within(org.springframework.web.bind.annotation.RestController) || " +
            "@within(org.springframework.stereotype.Controller)")
    public Object logHttpRequest(ProceedingJoinPoint joinPoint) throws Throwable {

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        // 设置MDC用于日志追踪
        MDC.put("requestId", requestId);
        MDC.put("traceId", requestId);

        try {
            // 记录请求信息
            logRequestDetails(request, joinPoint, requestId);

            // 执行实际方法
            Object result = joinPoint.proceed();

            // 记录响应信息
            long endTime = System.currentTimeMillis();
            logResponseDetails(response, result, joinPoint, requestId, endTime - startTime);

            return result;

        } catch (Exception e) {
            // 记录异常信息
            long endTime = System.currentTimeMillis();
            logErrorDetails(e, joinPoint, requestId, endTime - startTime);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    private void logRequestDetails(HttpServletRequest request, ProceedingJoinPoint joinPoint, String requestId) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("type", "REQUEST");
            logData.put("requestId", requestId);
            logData.put("timestamp", new Date());
            logData.put("method", request.getMethod());
            logData.put("uri", request.getRequestURI());
            logData.put("url", request.getRequestURL().toString());
            logData.put("queryString", request.getQueryString());

            // 请求头信息
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }
            logData.put("headers", headers);

            // 请求参数
            Map<String, String[]> parameters = request.getParameterMap();
            Map<String, Object> params = new HashMap<>();
            for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
                params.put(entry.getKey(), Arrays.asList(entry.getValue()));
            }
            logData.put("parameters", params);

            // 请求体（仅对POST/PUT/PATCH请求）
            String requestBody = getRequestBody(request);
            if (requestBody != null && !requestBody.isEmpty()) {
                logData.put("requestBody", requestBody);
            }

            // 类和方法信息
            logData.put("className", joinPoint.getTarget().getClass().getSimpleName());
            logData.put("methodName", joinPoint.getSignature().getName());
            logData.put("fullClassName", joinPoint.getTarget().getClass().getName());

            // 客户端信息
            logData.put("remoteAddr", getClientIpAddress(request));
            logData.put("userAgent", request.getHeader("User-Agent"));
            logData.put("referer", request.getHeader("Referer"));

            logger.info(objectMapper.writeValueAsString(logData));

        } catch (Exception e) {
            logger.error("Failed to log request details", e);
        }
    }

    private void logResponseDetails(HttpServletResponse response, Object result,
            ProceedingJoinPoint joinPoint, String requestId, long duration) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("type", "RESPONSE");
            logData.put("requestId", requestId);
            logData.put("timestamp", new Date());
            logData.put("duration", duration);
            logData.put("status", response.getStatus());

            // 响应头
            Map<String, String> responseHeaders = new HashMap<>();
            Collection<String> headerNames = response.getHeaderNames();
            for (String headerName : headerNames) {
                responseHeaders.put(headerName, response.getHeader(headerName));
            }
            logData.put("responseHeaders", responseHeaders);

            // 响应体（序列化结果对象）
            if (result != null) {
                try {
                    String responseBody = objectMapper.writeValueAsString(result);
                    logData.put("responseBody", responseBody);
                    logData.put("responseType", result.getClass().getSimpleName());
                } catch (Exception e) {
                    logData.put("responseBody", result.toString());
                    logData.put("responseType", result.getClass().getSimpleName());
                }
            }

            // 类和方法信息
            logData.put("className", joinPoint.getTarget().getClass().getSimpleName());
            logData.put("methodName", joinPoint.getSignature().getName());

            logger.info(objectMapper.writeValueAsString(logData));

        } catch (Exception e) {
            logger.error("Failed to log response details", e);
        }
    }

    private void logErrorDetails(Exception exception, ProceedingJoinPoint joinPoint,
            String requestId, long duration) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("type", "ERROR");
            logData.put("requestId", requestId);
            logData.put("timestamp", new Date());
            logData.put("duration", duration);
            logData.put("errorMessage", exception.getMessage());
            logData.put("errorClass", exception.getClass().getSimpleName());
            logData.put("stackTrace", getStackTrace(exception));

            // 类和方法信息
            logData.put("className", joinPoint.getTarget().getClass().getSimpleName());
            logData.put("methodName", joinPoint.getSignature().getName());

            logger.error(objectMapper.writeValueAsString(logData));

        } catch (Exception e) {
            logger.error("Failed to log error details", e);
        }
    }

    private String getRequestBody(HttpServletRequest request) {
        try {
            if ("GET".equalsIgnoreCase(request.getMethod()) ||
                    "DELETE".equalsIgnoreCase(request.getMethod())) {
                return null;
            }

            // 使用缓存的请求体
            if (request instanceof com.gui.app.config.CachedBodyHttpServletRequest) {
                return ((com.gui.app.config.CachedBodyHttpServletRequest) request).getBody();
            }

            // 降级方案：尝试读取请求体
            BufferedReader reader = request.getReader();
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Unable to read request body: " + e.getMessage();
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}