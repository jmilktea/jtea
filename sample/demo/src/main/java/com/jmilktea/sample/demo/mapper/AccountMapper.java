package com.jmilktea.sample.demo.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author huangyb1
 * @date 2020/5/29
 */
@Mapper
public interface AccountMapper {

	@Insert("insert into account(user_id) values(#{userId})")
	Integer insert(Integer userId);
}
