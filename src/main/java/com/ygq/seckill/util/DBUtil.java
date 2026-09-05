package com.ygq.seckill.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DBUtil {

	private static final String URL = "jdbc:mysql://localhost:3306/seckills?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
	private static final String USERNAME = "root";
	private static final String PASSWORD = "123456";
	private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

	static {
		try {
			Class.forName(DRIVER);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static Connection getConn() throws Exception {
		return DriverManager.getConnection(URL, USERNAME, PASSWORD);
	}
	
//	private static Properties props;
//
//	static {
//		try {
//			InputStream in = DBUtil.class.getClassLoader().getResourceAsStream("application.yaml");
//			props = new Properties();
//			props.load(in);
//			in.close();
//		}catch(Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	public static Connection getConn() throws Exception{
//		String url = props.getProperty("spring.datasource.url");
//		String username = props.getProperty("spring.datasource.username");
//		String password = props.getProperty("spring.datasource.password");
//		String driver = props.getProperty("spring.datasource.driver-class-name");
//		Class.forName(driver);
//		return DriverManager.getConnection(url,username, password);
//	}
}
