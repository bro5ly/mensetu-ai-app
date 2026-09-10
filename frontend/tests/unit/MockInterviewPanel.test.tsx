import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MockInterviewPanel } from "@/components/chat/MockInterviewPanel";
import type { MockChatState, MockProgress } from "@/hooks/useMockSession";
import type { ChatMessage } from "@/lib/types";

// jsdomはscrollIntoViewを実装していないため、MessageListの自動スクロール用にスタブする
Element.prototype.scrollIntoView = vi.fn();

function renderPanel(
  messages: ChatMessage[] = [],
  options: {
    chatState?: MockChatState;
    partialTranscript?: string;
    progress?: MockProgress | null;
    onEnd?: () => void;
    error?: string | null;
  } = {},
) {
  const defaultProgress: MockProgress = {
    questionText: "自己紹介をお願いします",
    questionIndex: 1,
    totalQuestions: 1,
  };
  return render(
    <MockInterviewPanel
      companyName="ABC商事"
      progress={"progress" in options ? (options.progress ?? null) : defaultProgress}
      messages={messages}
      chatState={options.chatState ?? "idle"}
      partialTranscript={options.partialTranscript ?? ""}
      micSupported
      error={options.error ?? null}
      audioPlaying={false}
      onToggleMic={() => {}}
      onSendText={() => {}}
      onStopAudio={() => {}}
      onEnd={options.onEnd ?? (() => {})}
      onDismissError={() => {}}
    />,
  );
}

describe("MockInterviewPanel の進捗表示", () => {
  it("会社名・質問文を表示する", () => {
    renderPanel();

    expect(screen.getByText("ABC商事 本番模擬面接")).toBeTruthy();
    expect(screen.getByText("自己紹介をお願いします")).toBeTruthy();
  });

  it("progressがnullの間は待機中の文言を表示する", () => {
    renderPanel([], { progress: null });

    expect(screen.getByText("面接官からの質問をお待ちください...")).toBeTruthy();
  });
});

describe("MockInterviewPanel のライブキャプション", () => {
  it("streaming中の自分の発話をマイクボタンの上に即時表示する", () => {
    renderPanel([
      { id: "m1", role: "USER", messageType: "NORMAL", content: "ゼミ活動をしていました", streaming: true },
    ]);

    expect(screen.getByTestId("live-caption").textContent).toBe("ゼミ活動をしていました");
  });

  it("録音中はプレビュー文字起こしをリアルタイムに表示する", () => {
    renderPanel([], { chatState: "recording", partialTranscript: "ゼミ活動を" });

    expect(screen.getByTestId("live-caption").textContent).toBe("ゼミ活動を");
  });
});

describe("MockInterviewPanel の終了確認", () => {
  it("終了するを押すと確認モーダルが出て、確定でonEndが呼ばれる", () => {
    const onEnd = vi.fn();
    renderPanel([], { onEnd });

    fireEvent.click(screen.getByRole("button", { name: "終了する" }));
    expect(screen.getByText("本番模擬面接を終了しますか？")).toBeTruthy();

    fireEvent.click(screen.getAllByRole("button", { name: "終了する" })[1]);
    expect(onEnd).toHaveBeenCalledTimes(1);
  });

  it("続けるを押すとモーダルが閉じてonEndは呼ばれない", () => {
    const onEnd = vi.fn();
    renderPanel([], { onEnd });

    fireEvent.click(screen.getByRole("button", { name: "終了する" }));
    fireEvent.click(screen.getByRole("button", { name: "続ける" }));

    expect(screen.queryByText("本番模擬面接を終了しますか？")).toBeNull();
    expect(onEnd).not.toHaveBeenCalled();
  });
});

describe("MockInterviewPanel のエラー表示", () => {
  it("エラーがあればアラートを表示する", () => {
    renderPanel([], { error: "接続エラーが発生しました" });

    expect(screen.getByRole("alert").textContent).toContain("接続エラーが発生しました");
  });
});
