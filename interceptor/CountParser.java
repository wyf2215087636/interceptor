package com.xd.base.sql.interceptor;

/**
 * @author WangYifei
 * @date 2021-06-04 14:43
 * @describe 总数SQL 解析器 *： 目前没有用到
 */
public interface CountParser {

    /**
     * 解析sql 查询总数
     * @param sql
     * @return
     */
    String parser(String sql, Object parameterObject);
}
