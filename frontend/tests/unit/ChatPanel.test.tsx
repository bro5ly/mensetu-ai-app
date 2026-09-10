import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ChatPanel } from "@/components/chat/ChatPanel";
import type { ActiveQuestion } from "@/hooks/usePracticeSession";
import type { ChatMessage } from "@/lib/types";

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    listSources: vi.fn(),
  },
}));

import { api } from "@/lib/api";

const listSources = vi.mocked(api.listSources);

// jsdomはscrollIntoViewを実装していないため、MessageListの自動スクロール用にスタブする
Element.prototype.scrollIntoView = vi.fn();

const active: ActiveQuestion = {
  companyName: "ABC商事",
  question: {
    id: "q1",
    companyId: "c1",
    questionText: "自己PRをしてください",
    internalCategory: null,
    displayOrder: 0,
    createdAt: "",
  },
};

function renderPanel(
  messages: ChatMessage[] = [],
  options: {
    chatState?: "idle" | "recording" | "processing" | "responding";
    partialTranscript?: string;
    onStartMock?: () => void;
  } = {},
) {
  return render(
    <ChatPanel
      active={active}
      messages={messages}
      chatState={options.chatState ?? "idle"}
      partialTranscript={options.partialTranscript ?? ""}
      micSupported
      error={null}
      audioPlaying={false}
      onToggleMic={() => {}}
      onSendText={() => {}}
      onStopAudio={() => {}}
      onEnd={() => {}}
      onDeleteQuestion={() => {}}
      onDismissError={() => {}}
      onStartMock={options.onStartMock ?? (() => {})}
    />,
  );
}

beforeEach(() => {
  listSources.mockReset();
});

describe("ChatPanel の本番開始導線", () => {
  it("本番を開始を押すとonStartMockが呼ばれる", () => {
    const onStartMock = vi.fn();
    renderPanel([], { onStartMock });

    fireEvent.click(screen.getByRole("button", { name: /本番を開始/ }));

    expect(onStartMock).toHaveBeenCalledTimes(1);
  });
});

describe("ChatPanel の参照ソース表示", () => {
  it("ソースボタンを押すと会社のソース一覧を取得して表示する", async () => {
    listSources.mockResolvedValue([
      {
        id: "s1",
        companyId: "c1",
        url: "https://example.com/a",
        title: "採用ページ",
        content: "本文",
        fetchedAt: "",
        createdAt: "",
      },
    ]);

    renderPanel();

    fireEvent.click(screen.getByTitle("登録済みのソースを見る"));

    expect(listSources).toHaveBeenCalledWith("c1");
    expect(await screen.findByText("採用ページ")).toBeTruthy();
    expect(screen.getByText("https://example.com/a")).toBeTruthy();
  });

  it("ソースが無い場合は空である旨を表示する", async () => {
    listSources.mockResolvedValue([]);

    renderPanel();

    fireEvent.click(screen.getByTitle("登録済みのソースを見る"));

    expect(await screen.findByText("登録されているソースはありません")).toBeTruthy();
  });

  it("2回目以降は再取得しない", async () => {
    listSources.mockResolvedValue([]);

    renderPanel();

    const button = screen.getByTitle("登録済みのソースを見る");
    fireEvent.click(button);
    await screen.findByText("登録されているソースはありません");

    fireEvent.click(button);
    fireEvent.click(button);

    expect(listSources).toHaveBeenCalledTimes(1);
  });
});

describe("ChatPanel のライブキャプション(音声ボタン上のリアルタイム表示)", () => {
  it("streaming中の自分の発話をマイクボタンの上に即時表示する", () => {
    renderPanel([
      {
        id: "m1",
        role: "USER",
        messageType: "NORMAL",
        content: "学生時代に力を入れたことについてです",
        streaming: true,
      },
    ]);

    expect(screen.getByTestId("live-caption").textContent).toBe(
      "学生時代に力を入れたことについてです",
    );
  });

  it("AIの応答はライブキャプションに表示しない(チャット吹き出し内の表示に一本化)", () => {
    renderPanel([
      {
        id: "m1",
        role: "ASSISTANT",
        messageType: "NORMAL",
        content: "既存のやり方にとらわれず",
        streaming: true,
      },
    ]);

    expect(screen.queryByTestId("live-caption")).toBeNull();
  });

  it("streaming中のメッセージが無ければライブキャプションを表示しない", () => {
    renderPanel([
      { id: "m1", role: "USER", messageType: "NORMAL", content: "確定済みの発話", streaming: false },
    ]);

    expect(screen.queryByTestId("live-caption")).toBeNull();
  });

  it("streaming中でも内容が空ならまだ表示しない", () => {
    renderPanel([
      { id: "m1", role: "USER", messageType: "NORMAL", content: "", streaming: true },
    ]);

    expect(screen.queryByTestId("live-caption")).toBeNull();
  });

  it("録音中はプレビュー文字起こし(partialTranscript)をリアルタイムに表示する", () => {
    renderPanel([], { chatState: "recording", partialTranscript: "学生時代に" });

    expect(screen.getByTestId("live-caption").textContent).toBe("学生時代に");
  });

  it("録音中でもプレビューがまだ空なら表示しない", () => {
    renderPanel([], { chatState: "recording", partialTranscript: "" });

    expect(screen.queryByTestId("live-caption")).toBeNull();
  });

  it("録音停止後(processing中)も確定結果が届くまではプレビューを表示し続ける", () => {
    renderPanel([], { chatState: "processing", partialTranscript: "学生時代に力を入れたこと" });

    expect(screen.getByTestId("live-caption").textContent).toBe(
      "学生時代に力を入れたこと",
    );
  });

  it("確定した文字起こし(streamingメッセージ)が届いたらプレビューより優先して表示する", () => {
    renderPanel(
      [
        {
          id: "m1",
          role: "USER",
          messageType: "NORMAL",
          content: "学生時代に力を入れたことです",
          streaming: true,
        },
      ],
      { chatState: "processing", partialTranscript: "学生時代に力を入れたこ" },
    );

    expect(screen.getByTestId("live-caption").textContent).toBe(
      "学生時代に力を入れたことです",
    );
  });
});
