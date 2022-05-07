package com.jmilktea.sample.demo.shutdown;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author huangyb1
 * @date 2022/5/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShutdownEvent {

	private Long time;
}
