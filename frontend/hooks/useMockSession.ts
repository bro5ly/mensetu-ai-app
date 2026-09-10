import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { createAudioQueuePlayer } from "@/lib/audioQueuePlayer";
import { PracticeSocket } from "@/lib/practiceSocket";
import { extractSentences } from "@/lib/sentenceSplitter";
import type { ChatMessage, MockReportResponse, MockSessionSummary } from "@/lib/types";
import { useRecorder } from "./useRecorder";

export type MockChatState = "idle" | "recording" | "processing" | "responding";
export type MockPhase = "selecting" | "connecting" | "interviewing" | "summary";

/** 本番模擬面接(質問ごとに独立した一問一答形式)の対象。 */
export interface ActiveMockQuestion {
  questionId: string;
  questionText: string;
  companyName: string;
}

export interface MockProgress {
  questionText: string;
  /** 1始まり。 */
  questionIndex: number;
  totalQuestions: number;
}

function newId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `id-${Math.random().toString(36).slice(2)}`;
}

/** レポートがまだ準備できていない間、この間隔でポーリングする(ユーザー強制終了時のみ)。 */
const REPORT_POLL_INTERVAL_MS = 2000;
/** ポーリングを諦めるまでの最大試行回数(既定間隔で約1分)。 */
const REPORT_POLL_MAX_ATTEMPTS = 30;

export function useMockSession() {
  const [phase, setPhase] = useState<MockPhase>("selecting");
  const [active, setActive] = useState<ActiveMockQuestion | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatState, setChatState] = useState<MockChatState>("idle");
  const [partialTranscript, setPartialTranscript] = useState("");
  const [progress, setProgress] = useState<MockProgress | null>(null);
  const [report, setReport] = useState<MockReportResponse | null>(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
  const [pastSessions, setPastSessions] = useState<MockSessionSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [audioPlaying, setAudioPlaying] = useState(false);

  const socketRef = useRef<PracticeSocket | null>(null);
  const streamingIdRef = useRef<string | null>(null);
  const audioPlayerRef = useRef(createAudioQueuePlayer(undefined, setAudioPlaying));
  const partialTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reportPollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const pendingSentencesRef = useRef<string[]>([]);
  const rawBufferRef = useRef("");

  const stopPartialTranscriptPolling = useCallback(() => {
    if (partialTimerRef.current !== null) {
      clearInterval(partialTimerRef.current);
      partialTimerRef.current = null;
    }
  }, []);

  const stopReportPolling = useCallback(() => {
    if (reportPollRef.current !== null) {
      clearInterval(reportPollRef.current);
      reportPollRef.current = null;
    }
  }, []);

  const teardownSocket = useCallback(() => {
    stopPartialTranscriptPolling();
    socketRef.current?.close();
    socketRef.current = null;
    audioPlayerRef.current.stop();
  }, [stopPartialTranscriptPolling]);

  useEffect(() => {
    return () => {
      teardownSocket();
      stopReportPolling();
    };
  }, [teardownSocket, stopReportPolling]);

  const recorder = useRecorder((chunk) => {
    socketRef.current?.sendAudioChunk(chunk);
  });

  const fetchReport = useCallback(async (targetSessionId: string) => {
    try {
      const result = await api.getMockReport(targetSessionId);
      setReport(result);
      setReportError(null);
      setReportLoading(false);
      return true;
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        return false;
      }
      setReportError(e instanceof ApiError ? e.message : "レポートを取得できませんでした");
      setReportLoading(false);
      return true;
    }
  }, []);

  const startInterview = useCallback(
    async ({ questionId, questionText, companyName }: ActiveMockQuestion) => {
      // 質問を切り替える際の不具合(2回押さないと反応しない等)を踏まえ、練習モードと同じく
      // 開始前に必ず前のセッションを片付ける。
      teardownSocket();
      stopReportPolling();
      setError(null);
      setPhase("connecting");
      setActive({ questionId, questionText, companyName });
      setChatState("idle");
      setPartialTranscript("");
      setProgress(null);
      setReport(null);
      setReportError(null);
      setReportLoading(false);
      setMessages([]);
      try {
        const started = await api.startMockSession(questionId);
        setSessionId(started.id);
        if (started.questions[0]) {
          setProgress({
            questionText: started.questions[0].questionText,
            questionIndex: 1,
            totalQuestions: started.questions.length,
          });
        }

        const socket = new PracticeSocket(started.id, {
          onTranscript: (text) => {
            setPartialTranscript("");
            setMessages((prev) => [
              ...prev,
              { id: newId(), role: "USER", messageType: "NORMAL", content: text, streaming: true },
            ]);
            setChatState("processing");
          },
          onPartialTranscript: (text) => setPartialTranscript(text),
          onAssistantStart: () => {
            const id = newId();
            streamingIdRef.current = id;
            pendingSentencesRef.current = [];
            rawBufferRef.current = "";
            setChatState("responding");
            setMessages((prev) => [
              ...prev.map((m) => (m.role === "USER" && m.streaming ? { ...m, streaming: false } : m)),
              { id, role: "ASSISTANT", messageType: "NORMAL", content: "", streaming: true },
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
            const targetId = streamingIdRef.current;
            audioPlayerRef.current.enqueue(audio, () => {
              if (!sentence || !targetId) return;
              setMessages((prev) =>
                prev.map((m) => (m.id === targetId ? { ...m, content: m.content + sentence } : m)),
              );
            });
          },
          onAssistantEnd: () => {
            const id = streamingIdRef.current;
            const leftover = pendingSentencesRef.current.join("") + rawBufferRef.current;
            pendingSentencesRef.current = [];
            rawBufferRef.current = "";
            streamingIdRef.current = null;
            setMessages((prev) =>
              prev.map((m) => (m.id === id ? { ...m, content: m.content + leftover, streaming: false } : m)),
            );
            setChatState("idle");
          },
          onMockQuestionAdvanced: (questionText, questionIndex, totalQuestions) => {
            setProgress({ questionText, questionIndex, totalQuestions });
          },
          onSessionEnded: () => {
            stopPartialTranscriptPolling();
            setChatState("idle");
          },
          onReportReady: () => {
            // AI判断による終了。サーバーがこの後すぐ接続を閉じるので、こちらでも片付けておく。
            setReportLoading(true);
            void fetchReport(started.id).then((done) => {
              if (done) {
                setPhase("summary");
                return;
              }
              // report_ready 受信直後の404は生成失敗を意味する(生成中はこのイベント自体が
              // 届かない)。念のため一度だけ待って再試行してから諦める。
              setTimeout(() => {
                void fetchReport(started.id).then((done2) => {
                  if (!done2) {
                    setReportLoading(false);
                    setReportError("レポートの生成に失敗しました。もう一度お試しください。");
                  }
                  setPhase("summary");
                });
              }, 1000);
            });
            teardownSocket();
          },
          onError: (message) => {
            stopPartialTranscriptPolling();
            setError(message);
            setChatState("idle");
          },
        });
        socket.connect();
        socketRef.current = socket;
        setPhase("interviewing");
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "面接を開始できませんでした");
        setPhase("selecting");
      }
    },
    [teardownSocket, stopPartialTranscriptPolling, stopReportPolling, fetchReport],
  );

  const sendText = useCallback((text: string) => {
    const trimmed = text.trim();
    if (!trimmed || !socketRef.current) return;
    setMessages((prev) => [
      ...prev,
      { id: newId(), role: "USER", messageType: "NORMAL", content: trimmed, streaming: false },
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

  /** ユーザーによる強制終了。レポート生成は非同期のため、準備できるまでポーリングする。 */
  const endInterview = useCallback(async () => {
    if (!sessionId) return;
    const targetSessionId = sessionId;
    socketRef.current?.forceEnd();
    teardownSocket();
    setPhase("summary");
    setChatState("idle");
    setReport(null);
    setReportError(null);
    setReportLoading(true);
    try {
      await api.endMockSession(targetSessionId);
    } catch (e) {
      setReportLoading(false);
      setReportError(e instanceof ApiError ? e.message : "面接を終了できませんでした");
      return;
    }

    let attempts = 0;
    reportPollRef.current = setInterval(() => {
      attempts += 1;
      void fetchReport(targetSessionId).then((done) => {
        if (done || attempts >= REPORT_POLL_MAX_ATTEMPTS) {
          stopReportPolling();
          if (!done) {
            setReportLoading(false);
            setReportError("レポートの生成に時間がかかっています。しばらくしてからもう一度お試しください。");
          }
        }
      });
    }, REPORT_POLL_INTERVAL_MS);
  }, [sessionId, teardownSocket, fetchReport, stopReportPolling]);

  const loadPastSessions = useCallback(async (questionId: string) => {
    try {
      setPastSessions(await api.listMockSessions(questionId));
    } catch {
      // 過去の挑戦一覧は付加情報のため、失敗しても画面は空のまま静かに諦める。
      setPastSessions([]);
    }
  }, []);

  const restartSame = useCallback(() => {
    if (active) void startInterview(active);
  }, [active, startInterview]);

  const reset = useCallback(() => {
    teardownSocket();
    stopReportPolling();
    setPhase("selecting");
    setActive(null);
    setSessionId(null);
    setMessages([]);
    setChatState("idle");
    setPartialTranscript("");
    setProgress(null);
    setReport(null);
    setReportError(null);
    setReportLoading(false);
    setPastSessions([]);
    setError(null);
  }, [teardownSocket, stopReportPolling]);

  return {
    phase,
    active,
    messages,
    chatState,
    partialTranscript,
    progress,
    report,
    reportLoading,
    reportError,
    pastSessions,
    error,
    audioPlaying,
    micSupported: recorder.isSupported,
    startInterview,
    sendText,
    toggleMic,
    stopAudio,
    endInterview,
    loadPastSessions,
    restartSame,
    reset,
    clearError: () => setError(null),
  };
}
