import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { createAudioQueuePlayer } from "@/lib/audioQueuePlayer";
import { PracticeSocket } from "@/lib/practiceSocket";
import { extractSentences } from "@/lib/sentenceSplitter";
import type {
  ChatMessage,
  MessageResponse,
  PracticeEndResponse,
  QuestionResponse,
} from "@/lib/types";
import { useRecorder } from "./useRecorder";

export type ChatState = "idle" | "recording" | "processing" | "responding";
export type Phase = "selecting" | "connecting" | "chatting" | "summary";

export interface ActiveQuestion {
  question: QuestionResponse;
  companyName: string;
}

function toChatMessage(m: MessageResponse): ChatMessage {
  return {
    id: m.id,
    role: m.role,
    messageType: m.messageType,
    content: m.content,
    streaming: false,
  };
}

function newId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `id-${Math.random().toString(36).slice(2)}`;
}

export function usePracticeSession() {
  const [phase, setPhase] = useState<Phase>("selecting");
  const [active, setActive] = useState<ActiveQuestion | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatState, setChatState] = useState<ChatState>("idle");
  /** 録音中のプレビュー文字起こし(数秒おきに更新)。確定結果は messages に入るのでここでは持たない。 */
  const [partialTranscript, setPartialTranscript] = useState("");
  const [summary, setSummary] = useState<PracticeEndResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [audioPlaying, setAudioPlaying] = useState(false);

  const socketRef = useRef<PracticeSocket | null>(null);
  const streamingIdRef = useRef<string | null>(null);
  const audioPlayerRef = useRef(createAudioQueuePlayer(undefined, setAudioPlaying));
  const partialTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPartialTranscriptPolling = useCallback(() => {
    if (partialTimerRef.current !== null) {
      clearInterval(partialTimerRef.current);
      partialTimerRef.current = null;
    }
  }, []);

  /**
   * AIの発話テキストは、対応する文の音声再生が始まったタイミングで表示する
   * (テキストと音声のずれを無くすため)。文が確定するたびに音声合成が始まる一方、
   * 実際に「表示」するのは audioPlayerRef の onStart コールバックからなので、
   * 未表示の文をここに溜めておき、音声到着順に1つずつ紐付ける。
   */
  const pendingSentencesRef = useRef<string[]>([]);
  const rawBufferRef = useRef("");

  const teardownSocket = useCallback(() => {
    stopPartialTranscriptPolling();
    socketRef.current?.close();
    socketRef.current = null;
    audioPlayerRef.current.stop();
  }, [stopPartialTranscriptPolling]);

  useEffect(() => teardownSocket, [teardownSocket]);

  const recorder = useRecorder((chunk) => {
    socketRef.current?.sendAudioChunk(chunk);
  });

  const openQuestion = useCallback(
    async ({ question, companyName }: ActiveQuestion) => {
      // 別の質問へ切り替える場合(会話の途中でサイドバーの別項目をクリックした場合など)、
      // 前のセッションのソケット/タイマーを明示的に片付けてから始める。これをしないと
      // 前のソケットが繋がったまま残り、そのハンドラが新しいセッションと共有された
      // state(chatState等)を後から書き換えてしまう。また chatState が前のセッションの
      // "processing"/"responding" 等のまま引き継がれると、新しい画面でマイクボタンが
      // 最初から disabled のまま表示され、何か別のイベントで偶然 idle に戻るまで
      // 1回目のクリックが効かない(2回押さないと録音が始まらない)不具合になっていた。
      teardownSocket();
      setError(null);
      setPhase("connecting");
      setActive({ question, companyName });
      setChatState("idle");
      setPartialTranscript("");
      setSummary(null);
      try {
        const started = await api.startPracticeSession(question.id);
        const detail = await api.getSession(started.id);
        setSessionId(detail.id);
        setMessages(detail.messages.map(toChatMessage));

        const socket = new PracticeSocket(detail.id, {
          onTranscript: (text) => {
            setPartialTranscript("");
            // 文字起こし結果は既に全文が確定しているため、演出のために小分けにする意味が
            // 無く(マイクボタン上のキャプションとチャット履歴の両方に同時に同じものを
            // 表示するだけになる)、そのまま即時に表示する。streaming はキャプション表示用の
            // フラグとして使い、AIの応答が始まったタイミングで消す(onAssistantStart参照)。
            setMessages((prev) => [
              ...prev,
              {
                id: newId(),
                role: "USER",
                messageType: "NORMAL",
                content: text,
                streaming: true,
              },
            ]);
            setChatState("processing");
          },
          onPartialTranscript: (text) => {
            setPartialTranscript(text);
          },
          onAssistantStart: (messageType) => {
            const id = newId();
            streamingIdRef.current = id;
            pendingSentencesRef.current = [];
            rawBufferRef.current = "";
            setChatState("responding");
            setMessages((prev) => [
              ...prev.map((m) =>
                m.role === "USER" && m.streaming ? { ...m, streaming: false } : m,
              ),
              {
                id,
                role: "ASSISTANT",
                messageType,
                content: "",
                streaming: true,
              },
            ]);
          },
          onAssistantChunk: (text) => {
            rawBufferRef.current += text;
            const { sentences, remainder } = extractSentences(rawBufferRef.current);
            rawBufferRef.current = remainder;
            pendingSentencesRef.current.push(...sentences);
          },
          onAssistantAudio: (audio) => {
            const sentence = pendingSentencesRef.current.shift();
            // 音声が実際に再生されるのはずっと後(前の文の再生完了後)になりうるため、
            // 対象メッセージIDは「今」の時点(=このターンがまだ進行中で確実に有効な時点)で
            // クロージャに固定しておく。streamingIdRef.current は既に次のターンやターン終了で
            // 変わっている可能性があり、再生開始時にそれを読み直すと反映先を見失う。
            const targetId = streamingIdRef.current;
            audioPlayerRef.current.enqueue(audio, () => {
              if (!sentence || !targetId) return;
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === targetId ? { ...m, content: m.content + sentence } : m,
                ),
              );
            });
          },
          onAssistantEnd: () => {
            // 音声が来なかった文(合成失敗等)や句読点で終わらなかった末尾の余りは、
            // 通常は音声のonStartで表示済みだが、念のためここでまとめて反映しておく。
            const id = streamingIdRef.current;
            const leftover = pendingSentencesRef.current.join("") + rawBufferRef.current;
            pendingSentencesRef.current = [];
            rawBufferRef.current = "";
            streamingIdRef.current = null;
            setMessages((prev) =>
              prev.map((m) =>
                m.id === id
                  ? { ...m, content: m.content + leftover, streaming: false }
                  : m,
              ),
            );
            setChatState("idle");
          },
          onSessionEnded: () => {
            stopPartialTranscriptPolling();
            setChatState("idle");
          },
          onError: (message) => {
            stopPartialTranscriptPolling();
            setError(message);
            setChatState("idle");
          },
        });
        socket.connect();
        socketRef.current = socket;
        setPhase("chatting");
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "セッションを開始できませんでした");
        setPhase("selecting");
      }
    },
    [teardownSocket, stopPartialTranscriptPolling],
  );

  const sendText = useCallback((text: string) => {
    const trimmed = text.trim();
    if (!trimmed || !socketRef.current) return;
    setMessages((prev) => [
      ...prev,
      {
        id: newId(),
        role: "USER",
        messageType: "NORMAL",
        content: trimmed,
        streaming: false,
      },
    ]);
    setChatState("processing");
    socketRef.current.sendUserText(trimmed);
  }, []);

  const startRecording = useCallback(async () => {
    if (!recorder.isSupported) {
      setError("このブラウザは録音に対応していません。テキスト入力を使ってください。");
      return;
    }
    try {
      await recorder.start();
      setChatState("recording");
      setPartialTranscript("");
      // 録音中は数秒おきにその時点までの音声をプレビュー文字起こしし、
      // マイクボタン上のキャプションをリアルタイムに更新する(本格的なストリーミングSTT)。
      // 前回のリクエストがまだ処理中の場合はサーバー側で無視されるだけなので、
      // クライアント側で多重送信を気にする必要はない。
      partialTimerRef.current = setInterval(() => {
        socketRef.current?.requestPartialTranscript();
      }, 2000);
    } catch {
      setError("マイクを使用できませんでした。権限を確認してください。");
    }
  }, [recorder]);

  const stopRecording = useCallback(() => {
    stopPartialTranscriptPolling();
    setChatState("processing");
    void recorder.stop().then(() => {
      socketRef.current?.endTurn();
    });
  }, [recorder, stopPartialTranscriptPolling]);

  const toggleMic = useCallback(() => {
    if (chatState === "recording") stopRecording();
    else if (chatState === "idle") void startRecording();
  }, [chatState, startRecording, stopRecording]);

  const stopAudio = useCallback(() => {
    audioPlayerRef.current.stop();
  }, []);

  const endPractice = useCallback(async () => {
    if (!sessionId) return;
    try {
      socketRef.current?.forceEnd();
      const result = await api.endSession(sessionId);
      setSummary(result);
    } catch (e) {
      setSummary({
        sessionId,
        messageCount: messages.length,
        lightSummary: e instanceof ApiError ? e.message : null,
      });
    } finally {
      teardownSocket();
      setPhase("summary");
      setChatState("idle");
    }
  }, [sessionId, messages.length, teardownSocket]);

  const restartSame = useCallback(() => {
    if (active) void openQuestion(active);
  }, [active, openQuestion]);

  const reset = useCallback(() => {
    teardownSocket();
    setPhase("selecting");
    setActive(null);
    setSessionId(null);
    setMessages([]);
    setChatState("idle");
    setPartialTranscript("");
    setSummary(null);
    setError(null);
  }, [teardownSocket]);

  return {
    phase,
    active,
    messages,
    chatState,
    partialTranscript,
    summary,
    error,
    audioPlaying,
    micSupported: recorder.isSupported,
    openQuestion,
    sendText,
    toggleMic,
    stopAudio,
    endPractice,
    restartSame,
    reset,
    clearError: () => setError(null),
  };
}
