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
 * @describe ?????? mybatis ????????????????????????mybatis ????????? 3.5.6??????????????????????????? #{ SqlParser } ????????????????????????SQL???
 */
@Setter
@Slf4j
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
// @Component
public class DataScopeInterceptor extends AbstractSqlParserHandler implements Interceptor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * COUNT SQL ??????
     */
    protected ISqlParser countSqlParser;
    /**
     * ????????????????????????????????????
     */
    protected boolean overflow = true;
    /**
     * ???????????? 500 ???????????? 0 ??? -1 ????????????
     */
    protected long limit = 500L;
    /**
     * ???????????????
     *
     * @since 3.3.1
     */
    private DbType dbType;
    /**
     * ???????????????
     *
     * @since 3.3.1
     */
    private IDialect dialect;
    /**
     * ????????????(????????????,?????????) <br>
     * ????????????????????????????????????????????????????????????????????????????????????????????????????????????
     *
     */
    @Deprecated
    protected String dialectType;
    /**
     * ???????????????<br>
     * ??????????????? com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect ???????????????
     *
     */
    @Deprecated
    protected String dialectClazz;

    /**
     * mybatis SQL ?????????
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        this.sqlParser(metaObject);
        // ???????????????SELECT ???????????????????????????
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (!SqlCommandType.SELECT.equals(mappedStatement.getSqlCommandType())) {
            return invocation.proceed();
        }
        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        // ?????????SQL??????
        String originalSql = boundSql.getSql();
        // SQL ????????????  ??????where ????????? ?????? ?????? limit ?????? ?????? ???????????????????????????????????????where id > 0
        /*if (originalSql.contains("WHERE") && originalSql.contains("LIMIT") && !originalSql.contains("JOIN")) {
            // ??????where ??????????????????where
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
        // ??????sql ?????????
        originalSql = sqlParser(originalSql, boundSql.getParameterObject(), count);
        metaObject.setValue("delegate.boundSql.sql", originalSql);

        return invocation.proceed();
    }

    /**
     * ???????????????????????? Limit ??????
     *
     * @param page ????????????
     * @return
     */
    protected boolean continueLimit(IPage<?> page) {
        if (page.getTotal() <= 0) {
            return false;
        }
        if (page.getCurrent() > page.getPages()) {
            if (this.overflow) {
                //?????????????????????
                handlerOverflow(page);
            } else {
                // ???????????????????????????????????????????????? list ??????
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
     * ?????????????????????
     *
     * @param sql             count sql
     * @param mappedStatement MappedStatement
     * @param boundSql        BoundSql
     * @param page            IPage
     * @param connection      Connection
     * @return true ???????????? false ?????? list ??????
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
     * sql ???????????????Bean ???????????????
     * @param sql
     * @return
     */
    private String sqlParser(String sql, Object parameterObject, Integer total) {
        // ?????? ????????? sql ????????? Bean
        String[] beanNamesForType = applicationContext.getBeanNamesForType(SqlParser.class);
        for (String beanName : beanNamesForType) {
            SqlParser sqlParser = (SqlParser) applicationContext.getBean(beanName);
            sql = sqlParser.parser(sql, parameterObject, total);
        }
        return sql;
    }

    /**
     * HPage ?????? ????????????
     * @param sql
     * @param parameterObject
     * @return
     */
    private String countParser(String sql, Object parameterObject) {
        // ?????? ????????? sql ????????? Bean
        String[] beanNamesForType = applicationContext.getBeanNamesForType(CountParser.class);
        for (String beanName : beanNamesForType) {
            CountParser countParser = (CountParser) applicationContext.getBean(beanName);
            sql = countParser.parser(sql, parameterObject);
        }
        return sql;
    }

    /**
     * ???????????????Count
     * @param originalSql
     * @param boundSql
     * @param mappedStatement
     * @return
     * @throws SQLException
     */
    private Integer initTotal(String originalSql, BoundSql boundSql, MappedStatement mappedStatement) throws SQLException {
        Integer count = 0;
        // ?????????????????????
        String countSql = countParser(originalSql, boundSql.getParameterObject());
        //??????????????????
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
     * ??????Mapper ?????? ??????????????????
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
