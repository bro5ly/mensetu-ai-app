import { useState } from "react";
import type { MockChatState, MockProgress } from "@/hooks/useMockSession";
import type { ChatMessage } from "@/lib/types";
import { Modal } from "@/components/ui/Modal";
import { Composer } from "./Composer";
import { MessageList } from "./MessageList";
import { MicButton } from "./MicButton";
import { WaveBars } from "./WaveBars";

interface Props {
  companyName: string;
  progress: MockProgress | null;
  messages: ChatMessage[];
  chatState: MockChatState;
  /** 録音中のプレビュー文字起こし(数秒おきに更新)。 */
  partialTranscript: string;
  micSupported: boolean;
  error: string | null;
  audioPlaying: boolean;
  onToggleMic: () => void;
  onSendText: (text: string) => void;
  onStopAudio: () => void;
  onEnd: () => void;
  onDismissError: () => void;
}

export function MockInterviewPanel({
  companyName,
  progress,
  messages,
  chatState,
  partialTranscript,
  micSupported,
  error,
  audioPlaying,
  onToggleMic,
  onSendText,
  onStopAudio,
  onEnd,
  onDismissError,
}: Props) {
  const busy = chatState === "processing" || chatState === "responding";
  const recording = chatState === "recording";

  const liveMessage = messages.find((m) => m.streaming && m.role === "USER");
  const captionText = liveMessage?.content || (recording || chatState === "processing" ? partialTranscript : "");

  const [confirmEnd, setConfirmEnd] = useState(false);

  return (
    <div className="relative flex h-full flex-1 flex-col">
      <header className="flex-shrink-0 px-8 pb-4 pt-[22px] shadow-[0_6px_10px_-8px_oklch(0.2_0.01_60/0.3)]">
        <div className="flex items-start justify-between gap-3.5">
          <div className="min-w-0 flex-1">
            <div className="mb-1 flex items-center gap-2 text-[12px] font-bold text-accent">
              <span>{companyName} 本番模擬面接</span>
            </div>
            <h1 className="text-[17px] font-semibold leading-[1.5] text-ink">
              {progress?.questionText ?? "面接官からの質問をお待ちください..."}
            </h1>
          </div>

          <button
            type="button"
            onClick={() => setConfirmEnd(true)}
            className="flex-shrink-0 rounded-[20px] border border-line bg-white px-3.5 py-2 text-[12.5px] font-semibold text-ink-soft transition hover:bg-panel-hover"
          >
            終了する
          </button>
        </div>

        {recording && (
          <div className="mt-3 flex items-center gap-[7px]">
            <span
              className="h-[7px] w-[7px] rounded-full bg-accent-strong"
              style={{ animation: "blinkDot 1.1s ease-in-out infinite" }}
            />
            <span className="text-[12.5px] font-semibold text-accent-strong">録音中...</span>
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

      <div className="flex flex-shrink-0 flex-col items-center gap-2 px-6 pb-4 pt-1">
        {recording ? <WaveBars /> : <div className="h-[34px]" aria-hidden />}

        {audioPlaying && (
          <button
            type="button"
            onClick={onStopAudio}
            className="flex items-center gap-1.5 rounded-full border border-line bg-white px-3.5 py-1.5 text-[12.5px] font-semibold text-ink-soft shadow-sm transition hover:bg-panel-hover"
          >
            <span className="h-2.5 w-2.5 rounded-[2px] bg-ink-soft" aria-hidden />
            音声を停止
          </button>
        )}

        {captionText && (
          <div
            data-testid="live-caption"
            className="max-w-[92%] rounded-2xl bg-accent-surface px-4 py-2.5 text-center text-[14px] leading-relaxed text-accent-ink"
          >
            {captionText}
          </div>
        )}

        {micSupported ? (
          <MicButton
            chatState={chatState}
            disabled={busy}
            onClick={onToggleMic}
            respondingLabel="面接官が応答中..."
          />
        ) : (
          <p className="text-[12.5px] text-ink-faint">
            録音非対応の環境です。テキストで回答してください。
          </p>
        )}

        <Composer disabled={busy} onSend={onSendText} />
      </div>

      {confirmEnd && (
        <Modal onClose={() => setConfirmEnd(false)} maxWidth={300}>
          <p className="mb-5 text-center text-[16px] font-semibold leading-[1.5] text-ink">
            本番模擬面接を終了しますか？
          </p>
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setConfirmEnd(false)}
              className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
            >
              続ける
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirmEnd(false);
                onEnd();
              }}
              className="flex-1 rounded-xl bg-accent px-3 py-2.5 text-sm font-semibold text-white"
            >
              終了する
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
