package com.xd.base.sql.interceptor;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisParameterHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.parser.ISqlParser;
import com.baomidou.mybatisplus.core.parser.SqlInfo;
import com.baomidou.mybatisplus.core.toolkit.ClassUtils;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.handlers.AbstractSqlParserHandler;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect;
import com.baomidou.mybatisplus.extension.toolkit.PropertyMapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlParserUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.Properties;

/**
 * @author WangYifei
 * @date 2021-06-04 9:50
 * @describe 魔改 mybatis 分页拦截器，兼容mybatis 版本， 3.5.6，可添加多个解析器 #{ SqlParser } 实现该接口，解析SQL。
 */
@Setter
@Slf4j
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
// @Component
public class DataScopeInterceptor extends AbstractSqlParserHandler implements Interceptor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * COUNT SQL 解析
     */
    protected ISqlParser countSqlParser;
    /**
     * 溢出总页数后是否进行处理
     */
    protected boolean overflow = true;
    /**
     * 单页限制 500 条，小于 0 如 -1 不受限制
     */
    protected long limit = 500L;
    /**
     * 数据库类型
     *
     * @since 3.3.1
     */
    private DbType dbType;
    /**
     * 方言实现类
     *
     * @since 3.3.1
     */
    private IDialect dialect;
    /**
     * 方言类型(数据库名,全小写) <br>
     * 如果用的我们支持分页的数据库但获取数据库类型不正确则可以配置该值进行校正
     *
     */
    @Deprecated
    protected String dialectType;
    /**
     * 方言实现类<br>
     * 注意！实现 com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect 接口的子类
     *
     */
    @Deprecated
    protected String dialectClazz;

    /**
     * mybatis SQL 拦截器
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        this.sqlParser(metaObject);
        // 判断是不是SELECT 操作，不是直接过滤
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (!SqlCommandType.SELECT.equals(mappedStatement.getSqlCommandType())) {
            return invocation.proceed();
        }
        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        // 执行的SQL语句
        String originalSql = boundSql.getSql();
        // SQL 执行参数  包含where 条件， 并且 包含 limit 分页 并且 不是连表，如果连表，自己写where id > 0
        /*if (originalSql.contains("WHERE") && originalSql.contains("LIMIT") && !originalSql.contains("JOIN")) {
            // 存在where 条件，则改变where
            String newWhere = "WHERE id > 0 AND ";
            originalSql = originalSql.replace("WHERE", newWhere);
        }*/

        IPage<?> page = ParameterUtils.findPage(boundSql.getParameterObject()).orElse(null);
        Connection connection = (Connection) invocation.getArgs()[0];
        if (page != null && page.isSearchCount() && !page.isHitCount()) {
            SqlInfo sqlInfo = SqlParserUtils.getOptimizeCountSql(page.optimizeCountSql(), countSqlParser, originalSql, metaObject);
            this.queryTotal(sqlInfo.getSql(), mappedStatement, boundSql, page, connection);
            if (!this.continueLimit(page)) {
                return null;
            }
        }
        // connection.close();


        Integer count = initTotal(originalSql, boundSql, mappedStatement);
        // 执行sql 解析器
        originalSql = sqlParser(originalSql, boundSql.getParameterObject(), count);
        metaObject.setValue("delegate.boundSql.sql", originalSql);

        return invocation.proceed();
    }

    /**
     * 判断是否继续执行 Limit 逻辑
     *
     * @param page 分页对象
     * @return
     */
    protected boolean continueLimit(IPage<?> page) {
        if (page.getTotal() <= 0) {
            return false;
        }
        if (page.getCurrent() > page.getPages()) {
            if (this.overflow) {
                //溢出总页数处理
                handlerOverflow(page);
            } else {
                // 超过最大范围，未设置溢出逻辑中断 list 执行
                return false;
            }
        }
        return true;
    }

    protected void handlerOverflow(IPage<?> page) {
        page.setCurrent(1);
        if (page instanceof HPage) {
            HPage<?> hPage = (HPage<?>) page;
            hPage.setId(0L);
        }
    }

    /**
     * 查询总记录条数
     *
     * @param sql             count sql
     * @param mappedStatement MappedStatement
     * @param boundSql        BoundSql
     * @param page            IPage
     * @param connection      Connection
     * @return true 继续执行 false 中断 list 执行
     */
    protected void queryTotal(String sql, MappedStatement mappedStatement, BoundSql boundSql, IPage<?> page, Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            MybatisParameterHandler parameterHandler = new MybatisParameterHandler(mappedStatement, boundSql.getParameterObject(), boundSql);
            parameterHandler.setParameters(statement);
            long total = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    total = resultSet.getLong(1);
                }
            }
            page.setTotal(total);
        } catch (Exception e) {
            throw ExceptionUtils.mpe("Error: Method queryTotal execution error of sql : \n %s \n", e, sql);
        }
    }

    /**
     * sql 解析器，从Bean 容器中获取
     * @param sql
     * @return
     */
    private String sqlParser(String sql, Object parameterObject, Integer total) {
        // 获取 全部的 sql 解析器 Bean
        String[] beanNamesForType = applicationContext.getBeanNamesForType(SqlParser.class);
        for (String beanName : beanNamesForType) {
            SqlParser sqlParser = (SqlParser) applicationContext.getBean(beanName);
            sql = sqlParser.parser(sql, parameterObject, total);
        }
        return sql;
    }

    /**
     * HPage 计算 总数解析
     * @param sql
     * @param parameterObject
     * @return
     */
    private String countParser(String sql, Object parameterObject) {
        // 获取 全部的 sql 解析器 Bean
        String[] beanNamesForType = applicationContext.getBeanNamesForType(CountParser.class);
        for (String beanName : beanNamesForType) {
            CountParser countParser = (CountParser) applicationContext.getBean(beanName);
            sql = countParser.parser(sql, parameterObject);
        }
        return sql;
    }

    /**
     * 初始化解析Count
     * @param originalSql
     * @param boundSql
     * @param mappedStatement
     * @return
     * @throws SQLException
     */
    private Integer initTotal(String originalSql, BoundSql boundSql, MappedStatement mappedStatement) throws SQLException {
        Integer count = 0;
        // 执行总数解析器
        String countSql = countParser(originalSql, boundSql.getParameterObject());
        //获取相关配置
        Configuration config = mappedStatement.getConfiguration();
        Connection connection = config.getEnvironment().getDataSource().getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(countSql);
        BoundSql countBoundSql = new BoundSql(config, countSql, boundSql.getParameterMappings(), boundSql.getParameterObject());
        ParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, boundSql.getParameterObject(), countBoundSql);
        parameterHandler.setParameters(preparedStatement);
        ResultSet rs = preparedStatement.executeQuery();
        if (rs.next()) {
            count = rs.getInt(1);
        }
        connection.close();
        return count;
    }

    /**
     * 获取Mapper 操作 获取总数计算
     * @param tClass
     * @param <T>
     * @return
     */
    private <T> BaseMapper<T> getMapper(Class<T> tClass) {
        String simpleName = tClass.getSimpleName();
        return null;
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        PropertyMapper.newInstance(properties)
                .whenNotBlack("countSqlParser", ClassUtils::newInstance, this::setCountSqlParser)
                .whenNotBlack("overflow", Boolean::parseBoolean, this::setOverflow)
                .whenNotBlack("dialectType", this::setDialectType)
                .whenNotBlack("dialectClazz", this::setDialectClazz)
                .whenNotBlack("dbType", DbType::getDbType, this::setDbType)
                .whenNotBlack("dialect", ClassUtils::newInstance, this::setDialect)
                .whenNotBlack("limit", Long::parseLong, this::setLimit);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
