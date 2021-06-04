package com.xd.base.sql.interceptor;

/**
 * @author WangYifei
 * @date 2021-06-04 10:09
 * @describe
 */
public interface SqlParser {
    String parser(String sql, Object parameterObject);

    String parser(String sql, Object parameterObject, Integer total);
}
