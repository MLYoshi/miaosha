package com.example.seckill.common;

import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TokenGeneratorTest {

    @Test
    public void generateTokensForJMeter() throws Exception {
        String csvPath = "/Users/yoshi/Desktop/code/jmeter/users_token.csv";
        Files.createDirectories(Paths.get("/Users/yoshi/Desktop/code/jmeter"));

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath))) {
            writer.println("userId,token");

            for (int i = 0; i < 2000; i++) {
                Long userId = 13000000000L + i;
                String token = JwtUtil.generateToken(userId);
                writer.println(userId + "," + token);
            }
        }

        System.out.println("Generated 2000 tokens at: " + csvPath);
    }
}
