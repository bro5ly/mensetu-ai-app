import type { ChatMessage } from "@/lib/types";

const cursor = (
  <span className="ml-0.5 inline-block h-4 w-[2px] animate-pulse bg-current align-middle" />
);

export function MessageBubble({ message }: { message: ChatMessage }) {
  if (message.role === "USER") {
    return (
      <div className="self-end max-w-[78%] rounded-2xl rounded-br-sm bg-violet-600 px-4 py-3 text-[14.5px] leading-relaxed text-white">
        {message.content}
      </div>
    );
  }

  if (message.messageType === "ADVICE") {
    return (
      <div className="self-start max-w-[82%] rounded-2xl border-[1.5px] border-violet-300 bg-violet-50 px-4 py-3 text-[14.5px] leading-relaxed text-neutral-800">
        <div className="mb-2 flex items-center gap-1.5">
          <span className="flex h-4 w-4 items-center justify-center rounded-full bg-violet-600 text-[11px] font-bold text-white">
            !
          </span>
          <span className="text-xs font-bold tracking-wide text-violet-700">
            アドバイス
          </span>
        </div>
        <div className="whitespace-pre-wrap">
          {message.content}
          {message.streaming && cursor}
        </div>
      </div>
    );
  }

  return (
    <div className="self-start max-w-[78%] whitespace-pre-wrap rounded-2xl rounded-bl-sm bg-neutral-100 px-4 py-3 text-[14.5px] leading-relaxed text-neutral-800">
      {message.content}
      {message.streaming && cursor}
    </div>
  );
}
