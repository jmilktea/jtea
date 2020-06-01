package com.jmilktea.sample.mybatis;

import com.jmilktea.sample.mybatis.entity.Account;
import com.jmilktea.sample.mybatis.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class MybatisApplication {

	@Autowired
	private AccountMapper accountMapper;

	public static void main(String[] args) {
		SpringApplication.run(MybatisApplication.class, args);
	}

	@RequestMapping("/account/{id}")
	public Account getAccountById(@PathVariable("id") Long id) {
		return accountMapper.getById(id);
	}

	@RequestMapping("/account/{id}/{userId}")
	public Account getAccountByIdAndUid(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
		return accountMapper.getByIdAndUid(id, userId);
	}
}
