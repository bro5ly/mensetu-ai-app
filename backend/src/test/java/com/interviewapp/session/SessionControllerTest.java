package com.interviewapp.session;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.mock.MockDtos.MockEndResponse;
import com.interviewapp.mock.MockDtos.MockReportResponse;
import com.interviewapp.mock.MockSessionService;
import com.interviewapp.session.SessionDtos.PracticeEndResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private MockSessionService mockSessionService;

    @Test
    void PRACTICEセッションの終了は200で軽いサマリーを返す() throws Exception {
        UUID sessionId = UUID.randomUUID();
        ChatSession practice = ChatSession.newPractice(UUID.randomUUID(), UUID.randomUUID());
        when(sessionService.findOrThrow(sessionId)).thenReturn(practice);
        when(sessionService.endByUser(sessionId))
                .thenReturn(new PracticeEndResponse(sessionId, 3, "練習を終了しました。"));

        mockMvc.perform(post("/api/sessions/{sessionId}/end", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCount").value(3));
    }

    @Test
    void MOCKセッションの終了は202でレポート保留状態を返す() throws Exception {
        UUID sessionId = UUID.randomUUID();
        ChatSession mock = ChatSession.newMock(UUID.randomUUID(), UUID.randomUUID());
        when(sessionService.findOrThrow(sessionId)).thenReturn(mock);
        when(mockSessionService.endByUser(sessionId)).thenReturn(new MockEndResponse(sessionId, "REPORT_PENDING"));

        mockMvc.perform(post("/api/sessions/{sessionId}/end", sessionId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REPORT_PENDING"));
    }

    @Test
    void レポートが準備できていれば200で返す() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(mockSessionService.getReport(sessionId)).thenReturn(Optional.of(
                new MockReportResponse(sessionId, 80, "良い傾向です", List.of(), null)));

        mockMvc.perform(get("/api/sessions/{sessionId}/report", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80));
    }

    @Test
    void レポートが未準備なら404() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(mockSessionService.getReport(sessionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionId}/report", sessionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void 存在しないセッションの終了は404() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.findOrThrow(sessionId)).thenThrow(new NotFoundException("見つかりません"));

        mockMvc.perform(post("/api/sessions/{sessionId}/end", sessionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void セッション詳細を返す() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.getDetail(sessionId)).thenReturn(new SessionDtos.SessionDetail(
                sessionId, UUID.randomUUID(), UUID.randomUUID(), SessionMode.PRACTICE, SessionStatus.READY,
                null, null, null, null, null, List.of()));

        mockMvc.perform(get("/api/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PRACTICE"));
    }
}
