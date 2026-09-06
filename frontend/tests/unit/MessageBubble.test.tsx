import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MessageBubble } from "@/components/chat/MessageBubble";
import type { ChatMessage } from "@/lib/types";

function message(overrides: Partial<ChatMessage>): ChatMessage {
  return {
    id: "1",
    role: "ASSISTANT",
    messageType: "NORMAL",
    content: "本文",
    streaming: false,
    ...overrides,
  };
}

describe("MessageBubble", () => {
  it("ユーザー発言はそのまま表示される", () => {
    render(<MessageBubble message={message({ role: "USER", content: "私の強みは継続力です" })} />);
    expect(screen.getByText("私の強みは継続力です")).toBeTruthy();
  });

  it("ADVICEメッセージは「アドバイス」ラベルを表示する", () => {
    render(
      <MessageBubble
        message={message({ messageType: "ADVICE", content: "結論から話しましょう" })}
      />,
    );
    expect(screen.getByText("アドバイス")).toBeTruthy();
    expect(screen.getByText("結論から話しましょう")).toBeTruthy();
  });

  it("通常のアシスタント応答はアドバイスラベルを出さない", () => {
    render(<MessageBubble message={message({ content: "もう少し詳しく教えてください" })} />);
    expect(screen.queryByText("アドバイス")).toBeNull();
    expect(screen.getByText("もう少し詳しく教えてください")).toBeTruthy();
  });

  it("streaming中で本文がまだ空なら「生成中...」を表示する", () => {
    render(<MessageBubble message={message({ content: "", streaming: true })} />);
    expect(screen.getByText("生成中...")).toBeTruthy();
    expect(screen.queryByText("再生中")).toBeNull();
  });

  it("streaming中で本文が表示され始めたら「再生中」に切り替わる", () => {
    render(<MessageBubble message={message({ content: "なるほど", streaming: true })} />);
    expect(screen.getByText("再生中")).toBeTruthy();
    expect(screen.queryByText("生成中...")).toBeNull();
  });

  it("streamingでなければ生成中/再生中いずれも表示しない", () => {
    render(<MessageBubble message={message({ content: "確定した本文", streaming: false })} />);
    expect(screen.queryByText("再生中")).toBeNull();
    expect(screen.queryByText("生成中...")).toBeNull();
  });
});
