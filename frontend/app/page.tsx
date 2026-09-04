"use client";

import { ChatPanel } from "@/components/chat/ChatPanel";
import { Sidebar } from "@/components/chat/Sidebar";
import { SummaryScreen } from "@/components/chat/SummaryScreen";
import { usePracticeSession } from "@/hooks/usePracticeSession";

export default function Home() {
  const session = usePracticeSession();

  return (
    <main className="flex h-screen w-full overflow-hidden bg-white text-neutral-900">
      <Sidebar
        activeQuestionId={session.active?.question.id ?? null}
        onSelectQuestion={(question, companyName) =>
          void session.openQuestion({ question, companyName })
        }
      />

      <section className="flex min-w-0 flex-1 flex-col">
        {session.phase === "selecting" && (
          <div className="flex h-full flex-col items-center justify-center gap-2 p-10 text-center">
            <div className="text-lg font-semibold text-neutral-700">
              練習する質問を選んでください
            </div>
            <p className="max-w-sm text-sm text-neutral-400">
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
          <div className="flex h-full items-center justify-center text-sm text-neutral-400">
            セッションを準備しています...
          </div>
        )}

        {session.phase === "chatting" && session.active && (
          <ChatPanel
            active={session.active}
            messages={session.messages}
            chatState={session.chatState}
            micSupported={session.micSupported}
            error={session.error}
            onToggleMic={session.toggleMic}
            onSendText={session.sendText}
            onEnd={() => void session.endPractice()}
            onDismissError={session.clearError}
          />
        )}

        {session.phase === "summary" && (
          <SummaryScreen summary={session.summary} onRestart={session.reset} />
        )}
      </section>
    </main>
  );
}
