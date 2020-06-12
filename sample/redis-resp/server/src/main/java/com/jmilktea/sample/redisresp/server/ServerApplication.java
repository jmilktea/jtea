package com.jmilktea.sample.redisresp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) throws IOException {
		SpringApplication.run(ServerApplication.class, args);

		ServerSocket serverSocket = new ServerSocket(16379);
		Socket socket = serverSocket.accept();
		InputStream inputStream = socket.getInputStream();
		byte[] arr = new byte[1024];
		int len;
		while ((len = inputStream.read(arr)) != -1) {
			String message = new String(arr, 0, len, Charset.forName("UTF-8"));
			System.out.println(message);
		}
	}

}
