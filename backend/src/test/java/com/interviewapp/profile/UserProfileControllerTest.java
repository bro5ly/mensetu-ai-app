package com.interviewapp.profile;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewapp.profile.UserProfileDtos.UserProfileResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserProfileController.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService service;

    @Test
    void プロフィールを取得する() throws Exception {
        when(service.get()).thenReturn(new UserProfileResponse("〇〇大学。強みは継続力。", Instant.now()));

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeText").value("〇〇大学。強みは継続力。"));
    }

    @Test
    void プロフィールを更新する() throws Exception {
        when(service.update(any())).thenReturn(new UserProfileResponse("更新後", Instant.now()));

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeText\":\"更新後\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeText").value("更新後"));
    }

    /**
     * フロントエンド(Next.js dev サーバー)はブラウザから直接PUTを叩くため、CORSの
     * allowedMethodsにPUTが含まれていないとプリフライト(OPTIONS)で拒否され、保存が
     * 「エラーが発生する」形で失敗する不具合が実際にあった({@code WebConfig}参照)。
     */
    @Test
    void PUTリクエストのCORSプリフライトを許可する() throws Exception {
        mockMvc.perform(options("/api/profile")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")));
    }
}
