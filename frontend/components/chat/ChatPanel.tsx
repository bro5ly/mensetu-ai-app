import { useEffect, useState } from "react";
import type { ActiveQuestion, ChatState } from "@/hooks/usePracticeSession";
import type { ChatMessage } from "@/lib/types";
import { Modal } from "@/components/ui/Modal";
import { Composer } from "./Composer";
import { MessageList } from "./MessageList";
import { MicButton } from "./MicButton";
import { WaveBars } from "./WaveBars";

interface Props {
  active: ActiveQuestion;
  messages: ChatMessage[];
  chatState: ChatState;
  micSupported: boolean;
  error: string | null;
  onToggleMic: () => void;
  onSendText: (text: string) => void;
  onEnd: () => void;
  onDeleteQuestion: () => void;
  onDismissError: () => void;
}

export function ChatPanel({
  active,
  messages,
  chatState,
  micSupported,
  error,
  onToggleMic,
  onSendText,
  onEnd,
  onDeleteQuestion,
  onDismissError,
}: Props) {
  const busy = chatState === "processing" || chatState === "responding";
  const recording = chatState === "recording";

  const [menuOpen, setMenuOpen] = useState(false);
  const [confirm, setConfirm] = useState<"end" | "delete" | null>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const close = () => setMenuOpen(false);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [menuOpen]);

  return (
    <div className="relative flex h-full flex-1 flex-col">
      <header className="flex-shrink-0 px-8 pb-4 pt-[22px] shadow-[0_6px_10px_-8px_oklch(0.2_0.01_60/0.3)]">
        <div className="flex items-start justify-between gap-3.5">
          <div className="min-w-0 flex-1">
            <div className="mb-1 text-[12px] font-bold text-accent">
              {active.companyName}
            </div>
            <h1 className="text-[17px] font-semibold leading-[1.5] text-ink">
              {active.question.questionText}
            </h1>
          </div>

          <div className="flex flex-shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => setConfirm("end")}
              className="rounded-[20px] border border-line bg-white px-3.5 py-2 text-[12.5px] font-semibold text-ink-soft transition hover:bg-panel-hover"
            >
              終了する
            </button>

            <div className="relative">
              <button
                type="button"
                title="その他の操作"
                onClick={(e) => {
                  e.stopPropagation();
                  setMenuOpen((v) => !v);
                }}
                className="flex h-8 w-8 items-center justify-center gap-[3px] rounded-lg transition hover:bg-panel-hover"
              >
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="h-[3.5px] w-[3.5px] rounded-full bg-ink-soft"
                  />
                ))}
              </button>
              {menuOpen && (
                <div className="absolute right-0 top-[38px] z-20 min-w-[140px] overflow-hidden rounded-[10px] border border-line bg-white shadow-[0_12px_30px_-10px_oklch(0.2_0.01_60/0.3)]">
                  <button
                    type="button"
                    onClick={() => {
                      setMenuOpen(false);
                      setConfirm("delete");
                    }}
                    className="w-full px-3.5 py-2.5 text-left text-[13px] font-medium text-ink transition hover:bg-panel-hover"
                  >
                    削除する
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {recording && (
          <div className="mt-3 flex items-center gap-[7px]">
            <span
              className="h-[7px] w-[7px] rounded-full bg-accent-strong"
              style={{ animation: "blinkDot 1.1s ease-in-out infinite" }}
            />
            <span className="text-[12.5px] font-semibold text-accent-strong">
              録音中...
            </span>
          </div>
        )}
      </header>

      {error && (
        <div
          role="alert"
          className="mx-8 mt-2 flex items-center justify-between rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600"
        >
          <span>{error}</span>
          <button type="button" onClick={onDismissError} className="ml-2 font-bold">
            ×
          </button>
        </div>
      )}

      <MessageList messages={messages} processing={chatState === "processing"} />

      <div className="flex flex-shrink-0 flex-col items-center gap-3 px-6 pb-[30px] pt-2.5">
        {recording ? (
          <WaveBars />
        ) : (
          <div className="h-[34px]" aria-hidden />
        )}

        {micSupported ? (
          <MicButton chatState={chatState} disabled={busy} onClick={onToggleMic} />
        ) : (
          <p className="text-[12.5px] text-ink-faint">
            録音非対応の環境です。テキストで回答してください。
          </p>
        )}

        <Composer disabled={busy} onSend={onSendText} />
      </div>

      {confirm === "end" && (
        <Modal onClose={() => setConfirm(null)} maxWidth={300}>
          <p className="mb-5 text-center text-[16px] font-semibold leading-[1.5] text-ink">
            この質問の練習を終了しますか？
          </p>
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setConfirm(null)}
              className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
            >
              続ける
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirm(null);
                onEnd();
              }}
              className="flex-1 rounded-xl bg-accent px-3 py-2.5 text-sm font-semibold text-white"
            >
              終了する
            </button>
          </div>
        </Modal>
      )}

      {confirm === "delete" && (
        <Modal onClose={() => setConfirm(null)} maxWidth={300}>
          <p className="mb-5 text-center text-[16px] font-semibold leading-[1.5] text-ink">
            この質問を削除しますか？
          </p>
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setConfirm(null)}
              className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
            >
              キャンセル
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirm(null);
                onDeleteQuestion();
              }}
              className="flex-1 rounded-xl bg-accent-strong px-3 py-2.5 text-sm font-semibold text-white"
            >
              削除する
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
