import { WS_BASE_URL } from "./config";
import type { MessageType } from "./types";

export interface PracticeSocketHandlers {
  onTranscript?: (text: string) => void;
  onAssistantStart?: (messageType: MessageType) => void;
  onAssistantChunk?: (text: string) => void;
  onAssistantEnd?: () => void;
  onAssistantAudio?: (audio: Blob) => void;
  onSessionEnded?: (reason: string) => void;
  onError?: (message: string) => void;
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
}

interface InboundEvent {
  type: string;
  text?: string;
  messageType?: MessageType;
  reason?: string;
  message?: string;
}

/**
 * 練習モードの WebSocket ({@code /ws/sessions/{sessionId}}) を扱うクライアント。
 * バックエンドのイベント種別をコールバックに振り分ける。
 */
export class PracticeSocket {
  private ws: WebSocket | null = null;

  constructor(
    private readonly sessionId: string,
    private readonly handlers: PracticeSocketHandlers = {},
  ) {}

  connect(): void {
    const ws = new WebSocket(`${WS_BASE_URL}/ws/sessions/${this.sessionId}`);
    ws.binaryType = "blob";
    this.ws = ws;

    ws.onopen = () => this.handlers.onOpen?.();
    ws.onclose = (event) => this.handlers.onClose?.(event);
    ws.onerror = () => this.handlers.onError?.("接続エラーが発生しました");
    ws.onmessage = (event) => this.handleMessage(event.data);
  }

  private handleMessage(data: unknown): void {
    if (data instanceof Blob) {
      this.handlers.onAssistantAudio?.(data);
      return;
    }
    if (typeof data !== "string") return;

    let event: InboundEvent;
    try {
      event = JSON.parse(data) as InboundEvent;
    } catch {
      return;
    }

    switch (event.type) {
      case "transcript":
        this.handlers.onTranscript?.(event.text ?? "");
        break;
      case "assistant_message_start":
        this.handlers.onAssistantStart?.(event.messageType ?? "NORMAL");
        break;
      case "assistant_text_chunk":
        this.handlers.onAssistantChunk?.(event.text ?? "");
        break;
      case "assistant_message_end":
        this.handlers.onAssistantEnd?.();
        break;
      case "session_ended":
        this.handlers.onSessionEnded?.(event.reason ?? "USER_ENDED");
        break;
      case "error":
        this.handlers.onError?.(event.message ?? "エラーが発生しました");
        break;
    }
  }

  private get isOpen(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  sendUserText(text: string): void {
    if (this.isOpen) this.ws!.send(JSON.stringify({ type: "user_text", text }));
  }

  sendAudioChunk(chunk: ArrayBuffer | Blob): void {
    if (this.isOpen) this.ws!.send(chunk);
  }

  endTurn(): void {
    if (this.isOpen) this.ws!.send(JSON.stringify({ type: "end_turn" }));
  }

  forceEnd(): void {
    if (this.isOpen) this.ws!.send(JSON.stringify({ type: "force_end" }));
  }

  close(): void {
    this.ws?.close();
    this.ws = null;
  }
}
