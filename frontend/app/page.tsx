"use client";

import { useCallback, useEffect, useState } from "react";
import { ChatPanel } from "@/components/chat/ChatPanel";
import { MockInterviewPanel } from "@/components/chat/MockInterviewPanel";
import { MockSummaryScreen } from "@/components/chat/MockSummaryScreen";
import { Sidebar } from "@/components/chat/Sidebar";
import { SummaryScreen } from "@/components/chat/SummaryScreen";
import { api } from "@/lib/api";
import type { QuestionResponse } from "@/lib/types";
import { usePracticeSession } from "@/hooks/usePracticeSession";
import { useMockSession } from "@/hooks/useMockSession";

export default function Home() {
  const session = usePracticeSession();
  const mock = useMockSession();
  const [sidebarReload, setSidebarReload] = useState(0);

  const userTurns = session.messages.filter((m) => m.role === "USER").length;
  const mockActive = mock.phase !== "selecting";

  useEffect(() => {
    if (mock.phase === "summary" && mock.active) {
      void mock.loadPastSessions(mock.active.questionId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mock.phase]);

  const handleDeleteQuestion = useCallback(async () => {
    const questionId = session.active?.question.id;
    if (!questionId) return;
    try {
      await api.deleteQuestion(questionId);
    } catch {
      // 失敗しても画面は選択状態へ戻す（サイドバー再取得で整合する）
    }
    setSidebarReload((n) => n + 1);
    session.reset();
  }, [session]);

  const handleSelectQuestion = useCallback(
    (question: QuestionResponse, companyName: string) => {
      mock.reset();
      void session.openQuestion({ question, companyName });
    },
    [session, mock],
  );

  const handleStartMockInterview = useCallback(() => {
    if (!session.active) return;
    const { question, companyName } = session.active;
    session.reset();
    void mock.startInterview({
      questionId: question.id,
      questionText: question.questionText,
      companyName,
    });
  }, [session, mock]);

  return (
    <main className="flex h-screen w-full overflow-hidden bg-white text-ink">
      <Sidebar
        activeQuestionId={session.active?.question.id ?? null}
        reloadSignal={sidebarReload}
        onSelectQuestion={handleSelectQuestion}
      />

      <section className="relative flex min-w-0 flex-1 flex-col bg-white">
        {!mockActive && session.phase === "selecting" && (
          <div className="flex h-full flex-col items-center justify-center gap-2 p-10 text-center">
            <div className="text-lg font-semibold text-ink">
              練習する質問を選んでください
            </div>
            <p className="max-w-sm text-sm text-ink-faint">
              左のリストから質問を選ぶとAIコーチとの練習が始まります。チャット画面右上の「本番を開始」から、その質問の本番模擬面接を始められます。
            </p>
            {session.error && (
              <p className="text-sm text-red-500" role="alert">
                {session.error}
              </p>
            )}
          </div>
        )}

        {!mockActive && session.phase === "connecting" && (
          <div className="flex h-full items-center justify-center text-sm text-ink-faint">
            セッションを準備しています...
          </div>
        )}

        {!mockActive && session.phase === "chatting" && session.active && (
          <ChatPanel
            active={session.active}
            messages={session.messages}
            chatState={session.chatState}
            partialTranscript={session.partialTranscript}
            micSupported={session.micSupported}
            error={session.error}
            audioPlaying={session.audioPlaying}
            onToggleMic={session.toggleMic}
            onSendText={session.sendText}
            onStopAudio={session.stopAudio}
            onEnd={() => void session.endPractice()}
            onDeleteQuestion={() => void handleDeleteQuestion()}
            onDismissError={session.clearError}
            onStartMock={handleStartMockInterview}
          />
        )}

        {!mockActive && session.phase === "summary" && (
          <SummaryScreen
            summary={session.summary}
            turnCount={userTurns}
            onPracticeAgain={session.restartSame}
            onBackToList={session.reset}
          />
        )}

        {mock.phase === "connecting" && (
          <div className="flex h-full items-center justify-center text-sm text-ink-faint">
            本番模擬面接を準備しています...
          </div>
        )}

        {mock.phase === "interviewing" && mock.active && (
          <MockInterviewPanel
            companyName={mock.active.companyName}
            progress={mock.progress}
            messages={mock.messages}
            chatState={mock.chatState}
            partialTranscript={mock.partialTranscript}
            micSupported={mock.micSupported}
            error={mock.error}
            audioPlaying={mock.audioPlaying}
            onToggleMic={mock.toggleMic}
            onSendText={mock.sendText}
            onStopAudio={mock.stopAudio}
            onEnd={() => void mock.endInterview()}
            onDismissError={mock.clearError}
          />
        )}

        {mock.phase === "summary" && mock.active && (
          <MockSummaryScreen
            companyName={mock.active.companyName}
            questionText={mock.active.questionText}
            report={mock.report}
            loading={mock.reportLoading}
            error={mock.reportError}
            pastSessions={mock.pastSessions}
            onRetryInterview={mock.restartSame}
            onBackToList={mock.reset}
          />
        )}
      </section>
    </main>
  );
}
