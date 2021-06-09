# interceptor
是一个工作中需要对mybatis生成的SQL 分页进行一个优化，自己写的扩展小工具，兼容mybatis。我用的版本是 3.5.6。

MyBatis is a work need to generate SQL pages for an optimization, their own written extension widgets, compatible with MyBatis. I used version 3.5.6.

`select tableField from tableName limit 1000000,10; 耗时：6.6秒`  
`select tableField from tableName where id > 1000000,10; 耗时:0.6秒`

使用方法：
```java
@Bean
public DataScopeInterceptor dataScopeInterceptor() {
	return new DataScopeInterceptor();
}
```

------
2021-06-09
新增 纸张自动排版实现
