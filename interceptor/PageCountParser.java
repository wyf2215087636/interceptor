package com.xd.base.sql.interceptor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import org.springframework.stereotype.Component;

/**
 * @author WangYifei
 * @date 2021-06-04 14:46
 * @describe 分页总数SQL 解析器的实现类
 */
@Component
public class PageCountParser implements CountParser{
    /**
     * 只支持 HPage 类型 总数计算
     * @param sql
     * @param parameterObject
     * @return
     */
    @Override
    public String parser(String sql, Object parameterObject) {
        IPage<?> page = ParameterUtils.findPage(parameterObject).orElse(null);
        if (page instanceof HPage) {
            // 等于HPage 强转
            if (sql.contains("WHERE") && !sql.contains("JOIN")) {
                // 存在where 条件，则改变where
                if (sql.contains("WHERE")) {
                    sql = sql.replace("WHERE id > 0", "WHERE").replaceAll("LIMIT\\s+\\d+(,\\d+)?", "");
                    sql = "SELECT COUNT(0) FROM ( " + sql + " ) AS total";
                }
            }
        }
        return sql;
    }
}
