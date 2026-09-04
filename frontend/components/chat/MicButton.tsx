import type { ChatState } from "@/hooks/usePracticeSession";

interface Props {
  chatState: ChatState;
  disabled: boolean;
  onClick: () => void;
}

const CAPTION: Record<ChatState, string> = {
  idle: "タップして話す",
  recording: "タップして送信",
  processing: "認識しています...",
  responding: "コーチが話しています...",
};

export function MicButton({ chatState, disabled, onClick }: Props) {
  const recording = chatState === "recording";

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative flex items-center justify-center">
        {recording && (
          <span className="absolute h-[76px] w-[76px] animate-ping rounded-full bg-violet-400/30" />
        )}
        <button
          type="button"
          onClick={onClick}
          disabled={disabled}
          aria-label={recording ? "録音を停止して送信" : "録音を開始"}
          className={`flex h-16 w-16 items-center justify-center rounded-full shadow-lg transition disabled:cursor-not-allowed disabled:opacity-40 ${
            recording ? "bg-violet-700" : "bg-violet-600 hover:bg-violet-700"
          }`}
        >
          {recording ? (
            <span className="h-5 w-5 rounded bg-white" />
          ) : (
            <span className="h-7 w-5 rounded-full bg-white" />
          )}
        </button>
      </div>
      <span className="text-xs font-medium text-neutral-500">
        {CAPTION[chatState]}
      </span>
    </div>
  );
}
