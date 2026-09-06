"use client";

import { useCallback, useState } from "react";
import { ChatPanel } from "@/components/chat/ChatPanel";
import { Sidebar } from "@/components/chat/Sidebar";
import { SummaryScreen } from "@/components/chat/SummaryScreen";
import { api } from "@/lib/api";
import { usePracticeSession } from "@/hooks/usePracticeSession";

export default function Home() {
  const session = usePracticeSession();
  const [sidebarReload, setSidebarReload] = useState(0);

  const userTurns = session.messages.filter((m) => m.role === "USER").length;

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

  return (
    <main className="flex h-screen w-full overflow-hidden bg-white text-ink">
      <Sidebar
        activeQuestionId={session.active?.question.id ?? null}
        reloadSignal={sidebarReload}
        onSelectQuestion={(question, companyName) =>
          void session.openQuestion({ question, companyName })
        }
      />

      <section className="relative flex min-w-0 flex-1 flex-col bg-white">
        {session.phase === "selecting" && (
          <div className="flex h-full flex-col items-center justify-center gap-2 p-10 text-center">
            <div className="text-lg font-semibold text-ink">
              練習する質問を選んでください
            </div>
            <p className="max-w-sm text-sm text-ink-faint">
              左のリストから質問を選ぶと、AIコーチとの練習が始まります。
            </p>
            {session.error && (
              <p className="text-sm text-red-500" role="alert">
                {session.error}
              </p>
            )}
          </div>
        )}

        {session.phase === "connecting" && (
          <div className="flex h-full items-center justify-center text-sm text-ink-faint">
            セッションを準備しています...
          </div>
        )}

        {session.phase === "chatting" && session.active && (
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
          />
        )}

        {session.phase === "summary" && (
          <SummaryScreen
            summary={session.summary}
            turnCount={userTurns}
            onPracticeAgain={session.restartSame}
            onBackToList={session.reset}
          />
        )}
      </section>
    </main>
  );
}
