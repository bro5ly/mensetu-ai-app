import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PracticeSocket } from "@/lib/practiceSocket";

class FakeWebSocket {
  static OPEN = 1;
  static CLOSED = 3;
  static instances: FakeWebSocket[] = [];

  readyState = FakeWebSocket.OPEN;
  binaryType = "";
  sent: unknown[] = [];
  onopen: (() => void) | null = null;
  onclose: ((e: unknown) => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((e: { data: unknown }) => void) | null = null;

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  send(data: unknown) {
    this.sent.push(data);
  }

  close() {
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.({});
  }

  emit(data: unknown) {
    this.onmessage?.({ data });
  }
}

beforeEach(() => {
  FakeWebSocket.instances = [];
  vi.stubGlobal("WebSocket", FakeWebSocket);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function connect(handlers = {}) {
  const socket = new PracticeSocket("session-1", handlers);
  socket.connect();
  return { socket, ws: FakeWebSocket.instances[0] };
}

describe("PracticeSocket", () => {
  it("セッションIDを含むURLで接続する", () => {
    const { ws } = connect();
    expect(ws.url).toBe("ws://localhost:8080/ws/sessions/session-1");
  });

  it("assistant系イベントをコールバックに振り分ける", () => {
    const onAssistantStart = vi.fn();
    const onAssistantChunk = vi.fn();
    const onAssistantEnd = vi.fn();
    const { ws } = connect({ onAssistantStart, onAssistantChunk, onAssistantEnd });

    ws.emit(JSON.stringify({ type: "assistant_message_start", messageType: "ADVICE" }));
    ws.emit(JSON.stringify({ type: "assistant_text_chunk", text: "結論から" }));
    ws.emit(JSON.stringify({ type: "assistant_message_end" }));

    expect(onAssistantStart).toHaveBeenCalledWith("ADVICE");
    expect(onAssistantChunk).toHaveBeenCalledWith("結論から");
    expect(onAssistantEnd).toHaveBeenCalledTimes(1);
  });

  it("transcript と session_ended と error を振り分ける", () => {
    const onTranscript = vi.fn();
    const onSessionEnded = vi.fn();
    const onError = vi.fn();
    const { ws } = connect({ onTranscript, onSessionEnded, onError });

    ws.emit(JSON.stringify({ type: "transcript", text: "こんにちは" }));
    ws.emit(JSON.stringify({ type: "session_ended", reason: "USER_ENDED" }));
    ws.emit(JSON.stringify({ type: "error", message: "失敗" }));

    expect(onTranscript).toHaveBeenCalledWith("こんにちは");
    expect(onSessionEnded).toHaveBeenCalledWith("USER_ENDED");
    expect(onError).toHaveBeenCalledWith("失敗");
  });

  it("partial_transcript をコールバックに渡す", () => {
    const onPartialTranscript = vi.fn();
    const { ws } = connect({ onPartialTranscript });

    ws.emit(JSON.stringify({ type: "partial_transcript", text: "学生時代に" }));

    expect(onPartialTranscript).toHaveBeenCalledWith("学生時代に");
  });

  it("バイナリフレームを音声コールバックに渡す", () => {
    const onAssistantAudio = vi.fn();
    const { ws } = connect({ onAssistantAudio });
    const blob = new Blob([new Uint8Array([1, 2, 3])]);

    ws.emit(blob);

    expect(onAssistantAudio).toHaveBeenCalledWith(blob);
  });

  it("mock_question_advanced と report_ready を振り分ける(本番モードのみ)", () => {
    const onMockQuestionAdvanced = vi.fn();
    const onReportReady = vi.fn();
    const { ws } = connect({ onMockQuestionAdvanced, onReportReady });

    ws.emit(JSON.stringify({
      type: "mock_question_advanced",
      questionText: "志望動機を教えてください",
      questionIndex: 2,
      totalQuestions: 5,
    }));
    ws.emit(JSON.stringify({ type: "report_ready" }));

    expect(onMockQuestionAdvanced).toHaveBeenCalledWith("志望動機を教えてください", 2, 5);
    expect(onReportReady).toHaveBeenCalledTimes(1);
  });

  it("不正なJSONは無視する", () => {
    const onError = vi.fn();
    const { ws } = connect({ onError });
    ws.emit("not json");
    expect(onError).not.toHaveBeenCalled();
  });

  it("send系メソッドが正しいペイロードを送る", () => {
    const { socket, ws } = connect();
    socket.sendUserText("テスト");
    socket.endTurn();
    socket.forceEnd();
    socket.requestPartialTranscript();

    expect(ws.sent).toEqual([
      JSON.stringify({ type: "user_text", text: "テスト" }),
      JSON.stringify({ type: "end_turn" }),
      JSON.stringify({ type: "force_end" }),
      JSON.stringify({ type: "request_partial_transcript" }),
    ]);
  });

  it("接続が閉じている場合は送信しない", () => {
    const { socket, ws } = connect();
    ws.readyState = FakeWebSocket.CLOSED;
    socket.sendUserText("x");
    expect(ws.sent).toHaveLength(0);
  });
});
