package com.xd.base.sql.interceptor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Component;

/**
 * @author WangYifei
 * @date 2021-06-04 10:10
 * @describe 分页SQL 解析器，兼容mybatis
 */
@Component
public class PageSqlParser implements SqlParser{
    /**
     * 解析sql 分页解析 优化
     * @param sql
     * @return
     */
    @Override
    public String parser(String sql, Object parameterObject) {
        return parser(sql, parameterObject, null);
    }

    @Override
    public String parser(String sql, Object parameterObject, Integer total) {
        IPage<?> page = ParameterUtils.findPage(parameterObject).orElse(null);
        HPage<?> hPage = null;
        if (page instanceof HPage) {
            // 等于HPage 强转
            hPage = (HPage<?>) page;
            if (sql.contains("WHERE") && !sql.contains("JOIN")) {
                // 存在where 条件，则改变where
                String newWhere = "WHERE id > " + hPage.getId() + " AND ";
                sql = sql.replace("WHERE", newWhere);
                sql += " LIMIT " + hPage.getSize();
            }
        } else if (page instanceof Page) {
            sql += " LIMIT " + ( page.getCurrent() - 1 ) * page.getSize() + "," + page.getSize();
        }
        return sql;
    }
}
