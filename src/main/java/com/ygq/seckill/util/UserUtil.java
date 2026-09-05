package com.ygq.seckill.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ygq.seckill.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserUtil {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private static void createUser(int count) throws Exception {
		System.out.println("开始生成 " + count + " 个用户...");
		List<User> users = new ArrayList<>(count);
		// 降低 BCrypt 强度（仅测试用，生产环境请使用 10 或更高）
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
		for (int i = 0; i < count; i++) {
			User user = new User();
			user.setId(13000000000L + i);
			user.setLoginCount(1);
			user.setNickname("user" + i);
			user.setRegisterDate(LocalDateTime.now());
			user.setPassword(encoder.encode("123456"));
			user.setSalt("");
			users.add(user);
			if (i % 1000 == 0) {
				System.out.println("已生成 " + (i + 1) + " / " + count + " 个用户对象");
			}
		}
		System.out.println("用户对象创建完成，开始插入数据库...");

		try (Connection conn = DBUtil.getConn();
			 PreparedStatement pstmt = conn.prepareStatement(
					 "INSERT INTO sk_user (login_count, nickname, register_date, salt, password, id) VALUES (?, ?, ?, ?, ?, ?)")) {
			int batchSize = 1000;
			for (int i = 0; i < users.size(); i++) {
				User user = users.get(i);
				pstmt.setInt(1, user.getLoginCount());
				pstmt.setString(2, user.getNickname());
				pstmt.setTimestamp(3, Timestamp.valueOf(user.getRegisterDate()));
				pstmt.setString(4, user.getSalt());
				pstmt.setString(5, user.getPassword());
				pstmt.setLong(6, user.getId());
				pstmt.addBatch();
				if ((i + 1) % batchSize == 0 || i == users.size() - 1) {
					pstmt.executeBatch();
					System.out.println("已插入 " + (i + 1) + " 条记录");
				}
			}
			System.out.println("数据库插入完成，共 " + users.size() + " 条记录。");
		} catch (Exception e) {
			System.err.println("数据库插入失败：" + e.getMessage());
			throw e;
		}

		// 登录生成 token
		String urlString = "http://localhost:8088/api/login";
		File file = new File("D:/tokens.txt");
		if (file.exists()) {
			file.delete();
		}
		try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
			for (int i = 0; i < users.size(); i++) {
				User user = users.get(i);
				try {
					URL url = new URL(urlString);
					HttpURLConnection co = (HttpURLConnection) url.openConnection();
					co.setRequestMethod("POST");
					co.setDoOutput(true);
					co.setRequestProperty("Content-Type", "application/json");
					co.setRequestProperty("Accept", "application/json");

					String jsonBody = "{\"mobile\":\"" + user.getId() + "\",\"password\":\"123456\"}";
					try (OutputStream out = co.getOutputStream()) {
						out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
						out.flush();
					}

					int responseCode = co.getResponseCode();
					InputStream inputStream = (responseCode >= 200 && responseCode < 300)
							? co.getInputStream()
							: co.getErrorStream();

					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					byte[] buff = new byte[1024];
					int len;
					while ((len = inputStream.read(buff)) >= 0) {
						bout.write(buff, 0, len);
					}
					inputStream.close();
					bout.close();

					String response = new String(bout.toByteArray(), StandardCharsets.UTF_8);
					JsonNode jo = objectMapper.readTree(response);
					String token = jo.path("data").asText();
					if (token != null && !token.isEmpty()) {
						String row = user.getId() + "," + token;
						raf.write(row.getBytes());
						raf.write("\r\n".getBytes());
						if (i % 1000 == 0) {
							System.out.println("已生成 token " + (i + 1) + " / " + users.size());
						}
					} else {
						System.err.println("WARN: token is empty for user " + user.getId());
					}
				} catch (Exception e) {
					System.err.println("处理用户 " + user.getId() + " 时出错：" + e.getMessage());
					e.printStackTrace();
				}
			}
		}
		System.out.println("所有 token 生成完毕，文件保存在 D:/tokens.txt");
	}

	public static void main(String[] args) {
		try {
			// 先测试少量用户，建议 1000 或 2000，确认流程正常再改大
			createUser(100000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
