package com.jmilktea.sample.mybatis.mapper;

import com.jmilktea.sample.mybatis.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author huangyb1
 * @date 2020/5/29
 */
@Mapper
public interface AccountMapper {

	@Select("select id,user_id as userId from account where id = #{id}")
	Account getById(@Param("id") Long id);

	@Select("select id,user_id as userId from account where id = #{id} and user_id = #{userId}")
	Account getByIdAndUid(@Param("id") Long id, @Param("userId") Long userId);
}
