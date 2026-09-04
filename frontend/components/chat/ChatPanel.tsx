import type { ActiveQuestion, ChatState } from "@/hooks/usePracticeSession";
import type { ChatMessage } from "@/lib/types";
import { Composer } from "./Composer";
import { MessageList } from "./MessageList";
import { MicButton } from "./MicButton";

interface Props {
  active: ActiveQuestion;
  messages: ChatMessage[];
  chatState: ChatState;
  micSupported: boolean;
  error: string | null;
  onToggleMic: () => void;
  onSendText: (text: string) => void;
  onEnd: () => void;
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
  onDismissError,
}: Props) {
  const busy = chatState === "processing" || chatState === "responding";

  return (
    <div className="flex h-full flex-1 flex-col">
      <header className="flex items-start justify-between gap-4 border-b border-neutral-200 px-8 py-5 shadow-sm">
        <div className="min-w-0">
          <div className="text-xs font-bold text-violet-600">
            {active.companyName}
          </div>
          <h1 className="text-[17px] font-semibold leading-snug text-neutral-800">
            {active.question.questionText}
          </h1>
        </div>
        <button
          type="button"
          onClick={onEnd}
          className="flex-shrink-0 rounded-full border border-neutral-300 px-4 py-2 text-xs font-semibold text-neutral-500 hover:bg-neutral-50"
        >
          終了する
        </button>
      </header>

      {chatState === "recording" && (
        <div className="flex items-center gap-2 px-8 py-2">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-violet-500" />
          <span className="text-xs font-semibold text-violet-600">録音中...</span>
        </div>
      )}

      {error && (
        <div
          role="alert"
          className="mx-8 my-2 flex items-center justify-between rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600"
        >
          <span>{error}</span>
          <button type="button" onClick={onDismissError} className="ml-2 font-bold">
            ×
          </button>
        </div>
      )}

      <MessageList messages={messages} processing={chatState === "processing"} />

      <div className="flex flex-col items-center gap-3 px-6 pb-8 pt-3">
        {micSupported ? (
          <MicButton
            chatState={chatState}
            disabled={busy}
            onClick={onToggleMic}
          />
        ) : (
          <p className="text-xs text-neutral-400">
            録音非対応の環境です。テキストで回答してください。
          </p>
        )}
        <Composer disabled={busy} onSend={onSendText} />
      </div>
    </div>
  );
}
