import { WS_BASE_URL } from "./config";
import type { MessageType } from "./types";

export interface PracticeSocketHandlers {
  onTranscript?: (text: string) => void;
  /** 録音継続中のプレビュー文字起こし({@link PracticeSocket.requestPartialTranscript}への応答)。 */
  onPartialTranscript?: (text: string) => void;
  onAssistantStart?: (messageType: MessageType) => void;
  onAssistantChunk?: (text: string) => void;
  onAssistantEnd?: () => void;
  onAssistantAudio?: (audio: Blob) => void;
  onSessionEnded?: (reason: string) => void;
  onError?: (message: string) => void;
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
  /** 本番モードのみ。次の質問に進んだ(questionIndex/totalQuestionsは1始まり)。 */
  onMockQuestionAdvanced?: (questionText: string, questionIndex: number, totalQuestions: number) => void;
  /** 本番モードのみ。セッション終了後、バックグラウンド生成していたレポートの準備ができた。 */
  onReportReady?: () => void;
}

interface InboundEvent {
  type: string;
  text?: string;
  messageType?: MessageType;
  reason?: string;
  message?: string;
  questionText?: string;
  questionIndex?: number;
  totalQuestions?: number;
}

/**
 * 練習モード・本番モード共通の WebSocket ({@code /ws/sessions/{sessionId}}) を扱うクライアント。
 * バックエンドは`chat_sessions.mode`で振る舞いを分けるだけで同じエンドポイント・プロトコルを
 * 使うため、クライアント側もこの1クラスで両モードを扱う(本番固有のイベントは
 * `onMockQuestionAdvanced`/`onReportReady`)。
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
      case "partial_transcript":
        this.handlers.onPartialTranscript?.(event.text ?? "");
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
      case "mock_question_advanced":
        this.handlers.onMockQuestionAdvanced?.(
          event.questionText ?? "",
          event.questionIndex ?? 0,
          event.totalQuestions ?? 0,
        );
        break;
      case "report_ready":
        this.handlers.onReportReady?.();
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

  /**
   * 録音継続中に、その時点までの音声のプレビュー文字起こしをリクエストする。
   * サーバー側は前回のリクエストがまだ処理中なら無視するので、一定間隔で
   * 呼び続けるだけでよい(多重リクエストの制御は不要)。
   */
  requestPartialTranscript(): void {
    if (this.isOpen) this.ws!.send(JSON.stringify({ type: "request_partial_transcript" }));
  }

  forceEnd(): void {
    if (this.isOpen) this.ws!.send(JSON.stringify({ type: "force_end" }));
  }

  close(): void {
    this.ws?.close();
    this.ws = null;
  }
}
