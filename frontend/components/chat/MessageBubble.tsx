import type { ChatMessage } from "@/lib/types";

/** ui-design の「再生中」イコライザー表示。TTS 再生の代わりにストリーミング中に出す。 */
function PlayingIndicator() {
  return (
    <div className="mt-2 flex items-center gap-1.5">
      <div className="flex h-[11px] items-end gap-[2px]">
        {[0, 0.15, 0.3].map((delay) => (
          <span
            key={delay}
            className="w-[2.5px] origin-bottom rounded-[1px] bg-accent"
            style={{
              height: "100%",
              animation: "eqBar 0.7s ease-in-out infinite",
              animationDelay: `${delay}s`,
            }}
          />
        ))}
      </div>
      <span className="text-[11px] text-ink-soft">再生中</span>
    </div>
  );
}

export function MessageBubble({ message }: { message: ChatMessage }) {
  if (message.role === "USER") {
    return (
      <div className="max-w-[78%] self-end rounded-[16px_16px_4px_16px] bg-accent px-4 py-3 text-[14.5px] leading-[1.55] text-white">
        {message.content}
      </div>
    );
  }

  if (message.messageType === "ADVICE") {
    return (
      <div className="max-w-[82%] self-start rounded-2xl border-[1.5px] border-accent-border bg-accent-bubble px-4 py-[13px] text-[14.5px] leading-[1.55] text-ink">
        <div className="mb-2 flex items-center gap-1.5">
          <span className="flex h-4 w-4 items-center justify-center rounded-full bg-accent text-[11px] font-bold text-white">
            !
          </span>
          <span className="text-[12px] font-bold tracking-[0.02em] text-accent-strong">
            アドバイス
          </span>
        </div>
        <div className="whitespace-pre-wrap">{message.content}</div>
      </div>
    );
  }

  return (
    <div className="max-w-[78%] self-start whitespace-pre-wrap rounded-[16px_16px_16px_4px] bg-panel-bubble px-4 py-3 text-[14.5px] leading-[1.55] text-ink">
      <div>{message.content}</div>
      {message.streaming && <PlayingIndicator />}
    </div>
  );
}
