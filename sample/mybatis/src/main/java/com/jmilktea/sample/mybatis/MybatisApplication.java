package com.jmilktea.sample.mybatis;

import com.jmilktea.sample.mybatis.entity.Account;
import com.jmilktea.sample.mybatis.mapper.AccountMapper;
import com.jmilktea.sample.mybatis.spring.MyMapper;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScannerRegistrar;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//@MapperScan(basePackages = "com.jmilktea.sample.mybatis")
@RestController
@SpringBootApplication
public class MybatisApplication {

	@Autowired
	private AccountMapper accountMapper;
//	@Autowired
//	private MyMapper myMapper;

	public static void main(String[] args) {
		//MybatisAutoConfiguration.AutoConfiguredMapperScannerRegistrar
		SpringApplication.run(MybatisApplication.class, args);
	}

	@Transactional
	@RequestMapping("/account/{id}")
	public Account getAccountById(@PathVariable("id") Long id) {
		return accountMapper.getById(id);
	}

	@RequestMapping("/account/{id}/{userId}")
	public Account getAccountByIdAndUid(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
		return accountMapper.getByIdAndUid(id, userId);
	}
}
