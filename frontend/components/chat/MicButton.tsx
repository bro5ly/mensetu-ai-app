import type { ChatState } from "@/hooks/usePracticeSession";

interface Props {
  chatState: ChatState;
  disabled: boolean;
  onClick: () => void;
  /** "responding"時の文言だけ差し替えたい場合(本番モードでは「面接官」表記にする等)。 */
  respondingLabel?: string;
}

const CAPTION: Record<ChatState, string> = {
  idle: "タップして話す",
  recording: "タップして停止",
  processing: "文字起こし中...",
  responding: "AIコーチが応答中...",
};

export function MicButton({ chatState, disabled, onClick, respondingLabel }: Props) {
  const recording = chatState === "recording";
  const caption =
    chatState === "responding" && respondingLabel ? respondingLabel : CAPTION[chatState];

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative flex items-center justify-center">
        {recording && (
          <span
            className="absolute h-[76px] w-[76px] rounded-full bg-accent-ring"
            style={{ animation: "pulseRing 1.6s ease-out infinite" }}
          />
        )}
        <button
          type="button"
          onClick={onClick}
          disabled={disabled}
          aria-label={recording ? "録音を停止して送信" : "録音を開始"}
          className={`relative flex h-[76px] w-[76px] items-center justify-center rounded-full transition-colors disabled:cursor-not-allowed ${
            recording
              ? "bg-accent-strong"
              : disabled
                ? "bg-panel-muted"
                : "bg-accent"
          }`}
        >
          {recording ? (
            <span className="h-[22px] w-[22px] rounded-md bg-white" />
          ) : (
            <span
              className={`h-[30px] w-5 rounded-[10px] ${
                disabled ? "bg-[oklch(0.7_0.006_60)]" : "bg-white"
              }`}
            />
          )}
        </button>
      </div>
      <span className="text-[12.5px] font-medium text-ink-soft">{caption}</span>
    </div>
  );
}
