package com.jmilktea.sample.redisresp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * @author huangyb1
 * @date 2020/6/11
 */
public class Client {

	public static void main(String[] args) throws IOException {
		Socket socket = new Socket("192.168.56.102", 6379);
		OutputStream outputStream = socket.getOutputStream();
		outputStream.write("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nhello\r\n".getBytes(Charset.forName("UTF-8")));
		outputStream.flush();
		InputStream inputStream = socket.getInputStream();
		byte[] arr = new byte[1024];
		int len = inputStream.read(arr);
		String message = new String(arr, 0, len, Charset.forName("UTF-8"));
		System.out.println(message);
	}
}
