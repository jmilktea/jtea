package com.jmilktea.sample.demo.service;

import com.jmilktea.sample.demo.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author huangyb1
 * @date 2020/9/2
 */
@Service
//@Transactional(rollbackFor = Exception.class)
public class TransService {

	@Autowired
	private AccountMapper accountMapper;

	@Transactional(rollbackFor = Exception.class)
	public void testTransaction() {
		accountMapper.insert(14);
		String s = null;
		int l = s.length();
		accountMapper.insert(15);
	}

	public void testTransactionCall() {
		testTransaction();
	}

	//@Transactional(rollbackFor = Exception.class)
	final public  void testTransactionFinal() {
		accountMapper.insert(14);
		String s = null;
		int l = s.length();
		accountMapper.insert(15);
	}
}
