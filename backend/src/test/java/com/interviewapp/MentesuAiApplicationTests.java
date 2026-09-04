package com.interviewapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

// WebSocket の ServerContainer 初期化に実サーブレットコンテナが要るため RANDOM_PORT で起動する
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MentesuAiApplicationTests {

    @Test
    void contextLoads() {
    }

}
