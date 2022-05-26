package com.jmilktea.sample.demo.mybean;

import com.jmilktea.sample.demo.mybean.bean.MyServiceClass;
import com.jmilktea.sample.demo.mybean.mapper.MyMapperInterface;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2022/5/24
 */
@Service
public class TestClass implements InitializingBean {

	@Autowired
	private MyServiceClass myServiceClass;

	@Autowired
	private MyMapperInterface myMapperInterface;

	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println(myServiceClass);
		myMapperInterface.test();
		//MyMapperInterface proxy = new JdkProxy().createProxy(MyMapperInterface.class);
		//proxy.test();

	}
}
