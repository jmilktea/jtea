package com.jmilktea.sample.demo.arthas;

import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2020/7/20
 */
@Service
public class ArthasService {

	public String test() {
		return "success";
	}

	public String testParam(ArthasParam param) {
		return param.getId();
	}
}
