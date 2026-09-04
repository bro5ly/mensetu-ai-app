import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { PracticeSocket } from "@/lib/practiceSocket";
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
  const [transcript, setTranscript] = useState("");
  const [summary, setSummary] = useState<PracticeEndResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const socketRef = useRef<PracticeSocket | null>(null);
  const streamingIdRef = useRef<string | null>(null);

  const appendChunk = useCallback((text: string) => {
    const id = streamingIdRef.current;
    if (!id) return;
    setMessages((prev) =>
      prev.map((m) => (m.id === id ? { ...m, content: m.content + text } : m)),
    );
  }, []);

  const teardownSocket = useCallback(() => {
    socketRef.current?.close();
    socketRef.current = null;
  }, []);

  useEffect(() => teardownSocket, [teardownSocket]);

  const recorder = useRecorder((chunk) => {
    socketRef.current?.sendAudioChunk(chunk);
  });

  const openQuestion = useCallback(
    async ({ question, companyName }: ActiveQuestion) => {
      setError(null);
      setPhase("connecting");
      setActive({ question, companyName });
      setSummary(null);
      try {
        const started = await api.startPracticeSession(question.id);
        const detail = await api.getSession(started.id);
        setSessionId(detail.id);
        setMessages(detail.messages.map(toChatMessage));

        const socket = new PracticeSocket(detail.id, {
          onTranscript: (text) => {
            setTranscript("");
            setMessages((prev) => [
              ...prev,
              {
                id: newId(),
                role: "USER",
                messageType: "NORMAL",
                content: text,
                streaming: false,
              },
            ]);
            setChatState("processing");
          },
          onAssistantStart: (messageType) => {
            const id = newId();
            streamingIdRef.current = id;
            setChatState("responding");
            setMessages((prev) => [
              ...prev,
              {
                id,
                role: "ASSISTANT",
                messageType,
                content: "",
                streaming: true,
              },
            ]);
          },
          onAssistantChunk: appendChunk,
          onAssistantEnd: () => {
            const id = streamingIdRef.current;
            streamingIdRef.current = null;
            setMessages((prev) =>
              prev.map((m) => (m.id === id ? { ...m, streaming: false } : m)),
            );
            setChatState("idle");
          },
          onSessionEnded: () => {
            setChatState("idle");
          },
          onError: (message) => {
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
    [appendChunk],
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
    } catch {
      setError("マイクを使用できませんでした。権限を確認してください。");
    }
  }, [recorder]);

  const stopRecording = useCallback(() => {
    recorder.stop();
    setChatState("processing");
    socketRef.current?.endTurn();
  }, [recorder]);

  const toggleMic = useCallback(() => {
    if (chatState === "recording") stopRecording();
    else if (chatState === "idle") void startRecording();
  }, [chatState, startRecording, stopRecording]);

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

  const reset = useCallback(() => {
    teardownSocket();
    setPhase("selecting");
    setActive(null);
    setSessionId(null);
    setMessages([]);
    setChatState("idle");
    setTranscript("");
    setSummary(null);
    setError(null);
  }, [teardownSocket]);

  return {
    phase,
    active,
    messages,
    chatState,
    transcript,
    summary,
    error,
    micSupported: recorder.isSupported,
    openQuestion,
    sendText,
    toggleMic,
    endPractice,
    reset,
    clearError: () => setError(null),
  };
}
