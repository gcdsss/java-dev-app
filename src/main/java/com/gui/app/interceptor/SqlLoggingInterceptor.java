package com.gui.app.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis SQL执行监控拦截器
 */
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }),
        @Signature(type = StatementHandler.class, method = "query", args = { Statement.class,
                org.apache.ibatis.session.ResultHandler.class }),
        @Signature(type = StatementHandler.class, method = "update", args = { Statement.class }),
        @Signature(type = StatementHandler.class, method = "batch", args = { Statement.class })
})
public class SqlLoggingInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(SqlLoggingInterceptor.class);

    @Autowired
    private ObjectMapper objectMapper;

    // 存储每个请求的SQL执行信息
    private static final Map<String, List<SqlExecutionInfo>> REQUEST_SQL_MAP = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        String requestId = MDC.get("requestId");
        String methodName = invocation.getMethod().getName();

        if (requestId == null) {
            // 如果没有requestId，直接执行
            return invocation.proceed();
        }

        // 只在query和update方法中记录，跳过prepare方法避免重复记录
        if ("prepare".equals(methodName)) {
            return invocation.proceed();
        }

        String sql = statementHandler.getBoundSql().getSql();
        String cleanSql = sql.replaceAll("\\s+", " ").trim();

        // 跳过SELECT语句的记录
        if (cleanSql.toLowerCase().startsWith("select")) {
            return invocation.proceed();
        }

        // 将SQL中的占位符替换为实际参数值
        String sqlWithValues = replaceSqlPlaceholders(statementHandler.getBoundSql());

        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = invocation.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 记录SQL执行信息
            SqlExecutionInfo sqlInfo = new SqlExecutionInfo();
            sqlInfo.setSql(sqlWithValues); // 使用带有实际值的SQL
            sqlInfo.setExecutionTime(executionTime);
            sqlInfo.setStartTime(startTime);
            sqlInfo.setEndTime(endTime);
            sqlInfo.setSuccess(exception == null);

            if (exception != null) {
                sqlInfo.setErrorMessage(exception.getMessage());
            } else {
                sqlInfo.setResultInfo(getResultInfo(result, methodName));
            }

            // 将SQL信息添加到当前请求的列表中
            REQUEST_SQL_MAP.computeIfAbsent(requestId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(sqlInfo);

            // 记录单条SQL日志
            logSqlExecution(requestId, sqlInfo);
        }
    }

    private void logSqlExecution(String requestId, SqlExecutionInfo sqlInfo) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("type", "SQL_EXECUTION");
            logData.put("requestId", requestId);
            logData.put("timestamp", new Date());
            logData.put("sql", sqlInfo.getSql());
            logData.put("executionTime", sqlInfo.getExecutionTime());
            logData.put("success", sqlInfo.isSuccess());

            if (!sqlInfo.isSuccess()) {
                logData.put("errorMessage", sqlInfo.getErrorMessage());
            } else {
                logData.put("resultInfo", sqlInfo.getResultInfo());
            }

            logger.info(objectMapper.writeValueAsString(logData));
        } catch (Exception e) {
            logger.error("Failed to log SQL execution", e);
        }
    }

    private String getResultInfo(Object result, String methodName) {
        if (result == null) {
            return "null";
        }

        if ("update".equals(methodName) || "batch".equals(methodName)) {
            return "Affected rows: " + result.toString();
        } else if ("query".equals(methodName)) {
            if (result instanceof List) {
                List<?> list = (List<?>) result;
                return "Query result count: " + list.size();
            } else {
                return "Query result: " + result.getClass().getSimpleName();
            }
        }

        return result.toString();
    }

    /**
     * 将SQL中的占位符替换为实际参数值
     */
    private String replaceSqlPlaceholders(BoundSql boundSql) {
        String sql = boundSql.getSql();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();

        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return sql;
        }

        try {
            // 按参数在SQL中出现的顺序替换
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                String propertyName = parameterMapping.getProperty();
                Object value = getParameterValue(parameterObject, propertyName);

                // 格式化参数值
                String formattedValue = formatParameterValue(value);

                // 替换第一个?为实际值
                sql = sql.replaceFirst("\\?", formattedValue);
            }
        } catch (Exception e) {
            logger.debug("Failed to replace SQL placeholders", e);
        }

        return sql;
    }

    /**
     * 获取参数值
     */
    private Object getParameterValue(Object parameterObject, String propertyName) {
        if (parameterObject == null) {
            return null;
        }

        if (parameterObject instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameterObject;
            return paramMap.get(propertyName);
        } else {
            // 单个参数或实体对象
            try {
                // 尝试通过反射获取属性值
                java.lang.reflect.Field field = parameterObject.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                return field.get(parameterObject);
            } catch (Exception e) {
                // 尝试getter方法
                try {
                    String getterName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
                    java.lang.reflect.Method getter = parameterObject.getClass().getMethod(getterName);
                    return getter.invoke(parameterObject);
                } catch (Exception ex) {
                    return parameterObject;
                }
            }
        }
    }

    /**
     * 格式化参数值用于SQL
     */
    private String formatParameterValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Date) {
            return "'" + value.toString() + "'";
        } else {
            return "'" + value.toString().replace("'", "''") + "'";
        }
    }

    /**
     * 获取指定请求的所有SQL执行信息
     */
    public static List<SqlExecutionInfo> getSqlExecutionInfo(String requestId) {
        return REQUEST_SQL_MAP.get(requestId);
    }

    /**
     * 清理指定请求的SQL执行信息
     */
    public static void clearSqlExecutionInfo(String requestId) {
        REQUEST_SQL_MAP.remove(requestId);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以在这里设置属性
    }

    /**
     * SQL执行信息实体类
     */
    public static class SqlExecutionInfo {
        private String sql;
        private long executionTime;
        private long startTime;
        private long endTime;
        private boolean success;
        private String errorMessage;
        private String resultInfo;

        // Getters and Setters
        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getResultInfo() {
            return resultInfo;
        }

        public void setResultInfo(String resultInfo) {
            this.resultInfo = resultInfo;
        }
    }
}
