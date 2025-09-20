package com.gui.app.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gui.app.interceptor.SqlLoggingInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SQL监控切面 - 记录每个请求的SQL执行汇总信息
 */
@Aspect
@Component
public class SqlMonitoringAspect {

    private static final Logger logger = LoggerFactory.getLogger(SqlMonitoringAspect.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@within(org.springframework.web.bind.annotation.RestController) || " +
            "@within(org.springframework.stereotype.Controller)")
    public Object monitorSqlExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String requestId = MDC.get("requestId");

        if (requestId == null) {
            // 如果没有requestId，直接执行
            return joinPoint.proceed();
        }

        long requestStartTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long requestEndTime = System.currentTimeMillis();

            // 获取当前请求的所有SQL执行信息
            List<SqlLoggingInterceptor.SqlExecutionInfo> sqlExecutions = SqlLoggingInterceptor
                    .getSqlExecutionInfo(requestId);

            if (sqlExecutions != null && !sqlExecutions.isEmpty()) {
                logSqlSummary(requestId, sqlExecutions, requestStartTime, requestEndTime,
                        joinPoint, exception == null);
            }

            // 清理SQL执行信息，避免内存泄漏
            SqlLoggingInterceptor.clearSqlExecutionInfo(requestId);
        }
    }

    private void logSqlSummary(String requestId,
            List<SqlLoggingInterceptor.SqlExecutionInfo> sqlExecutions,
            long requestStartTime, long requestEndTime,
            ProceedingJoinPoint joinPoint, boolean requestSuccess) {
        try {
            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("type", "SQL_SUMMARY");
            summaryData.put("requestId", requestId);
            summaryData.put("timestamp", new Date());
            summaryData.put("className", joinPoint.getTarget().getClass().getSimpleName());
            summaryData.put("methodName", joinPoint.getSignature().getName());
            summaryData.put("requestSuccess", requestSuccess);
            summaryData.put("totalRequestTime", requestEndTime - requestStartTime);

            // SQL执行统计
            int totalSqlCount = 0;
            int successfulSqlCount = 0;
            int failedSqlCount = 0;
            long totalSqlExecutionTime = 0;
            long minSqlTime = Long.MAX_VALUE;
            long maxSqlTime = 0;

            List<Map<String, Object>> sqlDetails = new ArrayList<>();

            for (SqlLoggingInterceptor.SqlExecutionInfo sqlInfo : sqlExecutions) {
                // 跳过SELECT语句的记录
                String sql = sqlInfo.getSql();
                if (sql != null && sql.trim().toLowerCase().startsWith("select")) {
                    continue;
                }

                totalSqlCount++;

                if (sqlInfo.isSuccess()) {
                    successfulSqlCount++;
                } else {
                    failedSqlCount++;
                }

                long executionTime = sqlInfo.getExecutionTime();
                totalSqlExecutionTime += executionTime;
                minSqlTime = Math.min(minSqlTime, executionTime);
                maxSqlTime = Math.max(maxSqlTime, executionTime);

                // 构建每条SQL的详细信息
                Map<String, Object> sqlDetail = new HashMap<>();
                sqlDetail.put("sql", sqlInfo.getSql());
                sqlDetail.put("executionTime", executionTime);
                sqlDetail.put("success", sqlInfo.isSuccess());

                if (!sqlInfo.isSuccess()) {
                    sqlDetail.put("errorMessage", sqlInfo.getErrorMessage());
                } else {
                    sqlDetail.put("resultInfo", sqlInfo.getResultInfo());
                }

                sqlDetails.add(sqlDetail);
            }

            // 添加统计信息
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalCount", totalSqlCount);
            statistics.put("successCount", successfulSqlCount);
            statistics.put("failedCount", failedSqlCount);
            statistics.put("totalExecutionTime", totalSqlExecutionTime);
            statistics.put("averageExecutionTime", totalSqlCount > 0 ? totalSqlExecutionTime / totalSqlCount : 0);
            statistics.put("minExecutionTime", minSqlTime == Long.MAX_VALUE ? 0 : minSqlTime);
            statistics.put("maxExecutionTime", maxSqlTime);

            summaryData.put("sqlStatistics", statistics);
            summaryData.put("sqlDetails", sqlDetails);

            // 性能分析
            Map<String, Object> performance = new HashMap<>();
            performance.put("sqlTimePercentage",
                    requestEndTime - requestStartTime > 0
                            ? (double) totalSqlExecutionTime / (requestEndTime - requestStartTime) * 100
                            : 0);
            performance.put("averageSqlTime", totalSqlCount > 0 ? totalSqlExecutionTime / totalSqlCount : 0);

            summaryData.put("performance", performance);

            logger.info(objectMapper.writeValueAsString(summaryData));

            // 如果有慢SQL或失败的SQL，记录警告日志
            if (failedSqlCount > 0) {
                logger.warn("Request {} has {} failed SQL executions", requestId, failedSqlCount);
            }

            if (maxSqlTime > 1000) { // 超过1秒的SQL
                logger.warn("Request {} has slow SQL execution: {} ms", requestId, maxSqlTime);
            }

        } catch (Exception e) {
            logger.error("Failed to log SQL summary for request: " + requestId, e);
        }
    }
}
