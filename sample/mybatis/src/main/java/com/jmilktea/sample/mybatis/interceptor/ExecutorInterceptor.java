package com.jmilktea.sample.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * @author huangyb1
 * @date 2020/5/29
 */
@Component
@Slf4j
@Intercepts({
		@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
		@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
		@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class ExecutorInterceptor implements Interceptor {

	private final static int SLOW_SQL_TIME = 500;

	@Override
	public Object plugin(Object target) {
		if (target instanceof Executor) {
			//如果是Executor才进行代理，减少非目标对象被代理次数
			return Plugin.wrap(target, this);
		}
		return target;
	}

	@Override
	public void setProperties(Properties properties) {
		//这里可以设置一些属性值，在intercept时使用
	}

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		//获取参数
		Object[] args = invocation.getArgs();
		//MappedStatement封装了mapper接口的方法
		MappedStatement mappedStatement = (MappedStatement) args[0];

		Long before = System.currentTimeMillis();
		Object result = invocation.proceed();
		Long after = System.currentTimeMillis();
		if (after - before > SLOW_SQL_TIME) {
			log.warn("slow sql:{}", mappedStatement.getBoundSql(args).getSql());
		}
		return result;
	}
}
