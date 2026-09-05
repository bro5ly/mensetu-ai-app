import { useEffect, useRef } from "react";
import type { ChatMessage } from "@/lib/types";
import { MessageBubble } from "./MessageBubble";

interface Props {
  messages: ChatMessage[];
  processing: boolean;
}

export function MessageList({ messages, processing }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, processing]);

  return (
    <div className="flex-1 overflow-y-auto px-8 pb-2 pt-6">
      <div className="flex flex-col gap-3.5">
        {messages.length === 0 && !processing && (
          <p className="mt-6 text-center text-[13px] leading-relaxed text-ink-faint">
            準備ができたら、下のマイクか入力欄から回答してみましょう。
          </p>
        )}

        {messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}

        {processing && (
          <div className="flex items-center gap-1.5 self-start rounded-[16px_16px_16px_4px] bg-panel-bubble px-[18px] py-3.5">
            {[0, 0.15, 0.3].map((delay) => (
              <span
                key={delay}
                className="h-1.5 w-1.5 rounded-full bg-ink-soft"
                style={{
                  animation: "bounceDot 1.2s infinite",
                  animationDelay: `${delay}s`,
                }}
              />
            ))}
          </div>
        )}

        <div ref={bottomRef} />
      </div>
    </div>
  );
}
