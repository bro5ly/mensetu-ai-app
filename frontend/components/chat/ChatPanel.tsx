import { useEffect, useState } from "react";
import type { ActiveQuestion, ChatState } from "@/hooks/usePracticeSession";
import { api, ApiError } from "@/lib/api";
import type { ChatMessage, SourceResponse } from "@/lib/types";
import { Modal } from "@/components/ui/Modal";
import { Composer } from "./Composer";
import { MessageList } from "./MessageList";
import { MicButton } from "./MicButton";
import { WaveBars } from "./WaveBars";

interface Props {
  active: ActiveQuestion;
  messages: ChatMessage[];
  chatState: ChatState;
  /** 録音中のプレビュー文字起こし(数秒おきに更新)。 */
  partialTranscript: string;
  micSupported: boolean;
  error: string | null;
  audioPlaying: boolean;
  onToggleMic: () => void;
  onSendText: (text: string) => void;
  onStopAudio: () => void;
  onEnd: () => void;
  onDeleteQuestion: () => void;
  onDismissError: () => void;
}

export function ChatPanel({
  active,
  messages,
  chatState,
  partialTranscript,
  micSupported,
  error,
  audioPlaying,
  onToggleMic,
  onSendText,
  onStopAudio,
  onEnd,
  onDeleteQuestion,
  onDismissError,
}: Props) {
  const busy = chatState === "processing" || chatState === "responding";
  const recording = chatState === "recording";

  // 自分の発話(文字起こし結果)だけのライブキャプション。AIの応答はチャット吹き出し内の
  // 「生成中.../再生中」表示に一本化したため、ここでは対象にしない(同じ内容が2箇所で
  // 同時に流れるのを避けるため)。表示は文字起こし結果をそのまま即時に見せる(速度優先)。
  const liveMessage = messages.find((m) => m.streaming && m.role === "USER");
  // 録音中は数秒おきに更新されるプレビュー文字起こしをそのまま見せ、
  // 録音停止後(end_turn の確定結果待ち〜表示)は直前のプレビューを引き継いで表示し続ける
  // ことでキャプションが一瞬空白に戻るのを防ぐ。確定結果が届くと liveMessage 側に切り替わる。
  const captionText = liveMessage?.content || (recording || chatState === "processing" ? partialTranscript : "");

  const [menuOpen, setMenuOpen] = useState(false);
  const [confirm, setConfirm] = useState<"end" | "delete" | null>(null);

  const [sourcesOpen, setSourcesOpen] = useState(false);
  const [sources, setSources] = useState<SourceResponse[] | null>(null);
  const [sourcesLoading, setSourcesLoading] = useState(false);
  const [sourcesError, setSourcesError] = useState<string | null>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const close = () => setMenuOpen(false);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [menuOpen]);

  useEffect(() => {
    if (!sourcesOpen) return;
    const close = () => setSourcesOpen(false);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [sourcesOpen]);

  const toggleSources = async () => {
    const opening = !sourcesOpen;
    setSourcesOpen(opening);
    if (!opening || sources !== null) return;
    setSourcesLoading(true);
    setSourcesError(null);
    try {
      setSources(await api.listSources(active.question.companyId));
    } catch (e) {
      setSourcesError(
        e instanceof ApiError ? e.message : "ソースを取得できませんでした",
      );
    } finally {
      setSourcesLoading(false);
    }
  };

  return (
    <div className="relative flex h-full flex-1 flex-col">
      <header className="flex-shrink-0 px-8 pb-4 pt-[22px] shadow-[0_6px_10px_-8px_oklch(0.2_0.01_60/0.3)]">
        <div className="flex items-start justify-between gap-3.5">
          <div className="min-w-0 flex-1">
            <div className="mb-1 text-[12px] font-bold text-accent">
              {active.companyName}
            </div>
            <h1 className="text-[17px] font-semibold leading-[1.5] text-ink">
              {active.question.questionText}
            </h1>
          </div>

          <div className="flex flex-shrink-0 items-center gap-2">
            <div className="relative">
              <button
                type="button"
                title="登録済みのソースを見る"
                onClick={(e) => {
                  e.stopPropagation();
                  void toggleSources();
                }}
                className="flex items-center gap-1.5 rounded-[20px] border border-line bg-white px-3 py-2 text-[12.5px] font-semibold text-ink-soft transition hover:bg-panel-hover"
              >
                <svg
                  width="13"
                  height="13"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden
                >
                  <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                  <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
                </svg>
                ソース{sources && sources.length > 0 ? ` ${sources.length}` : ""}
              </button>
              {sourcesOpen && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="absolute right-0 top-[42px] z-20 w-[300px] overflow-hidden rounded-[10px] border border-line bg-white shadow-[0_12px_30px_-10px_oklch(0.2_0.01_60/0.3)]"
                >
                  <div className="border-b border-line px-3.5 py-2.5 text-[11px] font-bold uppercase tracking-[0.04em] text-ink-soft">
                    参照ソース
                  </div>
                  <div className="max-h-[280px] overflow-y-auto">
                    {sourcesLoading && (
                      <p className="px-3.5 py-3 text-[13px] text-ink-faint">
                        読み込み中...
                      </p>
                    )}
                    {sourcesError && (
                      <p className="px-3.5 py-3 text-[13px] text-red-500" role="alert">
                        {sourcesError}
                      </p>
                    )}
                    {!sourcesLoading && !sourcesError && sources?.length === 0 && (
                      <p className="px-3.5 py-3 text-[13px] text-ink-faint">
                        登録されているソースはありません
                      </p>
                    )}
                    {!sourcesLoading &&
                      sources?.map((s) => (
                        <a
                          key={s.id}
                          href={s.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="block border-b border-line px-3.5 py-2.5 last:border-b-0 hover:bg-panel-hover"
                        >
                          <div className="truncate text-[13px] font-semibold text-ink">
                            {s.title || s.url}
                          </div>
                          <div className="truncate text-[11.5px] text-ink-faint">
                            {s.url}
                          </div>
                        </a>
                      ))}
                  </div>
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => setConfirm("end")}
              className="rounded-[20px] border border-line bg-white px-3.5 py-2 text-[12.5px] font-semibold text-ink-soft transition hover:bg-panel-hover"
            >
              終了する
            </button>

            <div className="relative">
              <button
                type="button"
                title="その他の操作"
                onClick={(e) => {
                  e.stopPropagation();
                  setMenuOpen((v) => !v);
                }}
                className="flex h-8 w-8 items-center justify-center gap-[3px] rounded-lg transition hover:bg-panel-hover"
              >
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="h-[3.5px] w-[3.5px] rounded-full bg-ink-soft"
                  />
                ))}
              </button>
              {menuOpen && (
                <div className="absolute right-0 top-[38px] z-20 min-w-[140px] overflow-hidden rounded-[10px] border border-line bg-white shadow-[0_12px_30px_-10px_oklch(0.2_0.01_60/0.3)]">
                  <button
                    type="button"
                    onClick={() => {
                      setMenuOpen(false);
                      setConfirm("delete");
                    }}
                    className="w-full px-3.5 py-2.5 text-left text-[13px] font-medium text-ink transition hover:bg-panel-hover"
                  >
                    削除する
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {recording && (
          <div className="mt-3 flex items-center gap-[7px]">
            <span
              className="h-[7px] w-[7px] rounded-full bg-accent-strong"
              style={{ animation: "blinkDot 1.1s ease-in-out infinite" }}
            />
            <span className="text-[12.5px] font-semibold text-accent-strong">
              録音中...
            </span>
          </div>
        )}
      </header>

      {error && (
        <div
          role="alert"
          className="mx-8 mt-2 flex items-center justify-between rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600"
        >
          <span>{error}</span>
          <button type="button" onClick={onDismissError} className="ml-2 font-bold">
            ×
          </button>
        </div>
      )}

      <MessageList messages={messages} processing={chatState === "processing"} />

      <div className="flex flex-shrink-0 flex-col items-center gap-2 px-6 pb-4 pt-1">
        {recording ? (
          <WaveBars />
        ) : (
          <div className="h-[34px]" aria-hidden />
        )}

        {audioPlaying && (
          <button
            type="button"
            onClick={onStopAudio}
            className="flex items-center gap-1.5 rounded-full border border-line bg-white px-3.5 py-1.5 text-[12.5px] font-semibold text-ink-soft shadow-sm transition hover:bg-panel-hover"
          >
            <span className="h-2.5 w-2.5 rounded-[2px] bg-ink-soft" aria-hidden />
            音声を停止
          </button>
        )}

        {captionText && (
          <div
            data-testid="live-caption"
            className="max-w-[92%] rounded-2xl bg-accent-surface px-4 py-2.5 text-center text-[14px] leading-relaxed text-accent-ink"
          >
            {captionText}
          </div>
        )}

        {micSupported ? (
          <MicButton chatState={chatState} disabled={busy} onClick={onToggleMic} />
        ) : (
          <p className="text-[12.5px] text-ink-faint">
            録音非対応の環境です。テキストで回答してください。
          </p>
        )}

        <Composer disabled={busy} onSend={onSendText} />
      </div>

      {confirm === "end" && (
        <Modal onClose={() => setConfirm(null)} maxWidth={300}>
          <p className="mb-5 text-center text-[16px] font-semibold leading-[1.5] text-ink">
            この質問の練習を終了しますか？
          </p>
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setConfirm(null)}
              className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
            >
              続ける
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirm(null);
                onEnd();
              }}
              className="flex-1 rounded-xl bg-accent px-3 py-2.5 text-sm font-semibold text-white"
            >
              終了する
            </button>
          </div>
        </Modal>
      )}

      {confirm === "delete" && (
        <Modal onClose={() => setConfirm(null)} maxWidth={300}>
          <p className="mb-5 text-center text-[16px] font-semibold leading-[1.5] text-ink">
            この質問を削除しますか？
          </p>
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setConfirm(null)}
              className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
            >
              キャンセル
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirm(null);
                onDeleteQuestion();
              }}
              className="flex-1 rounded-xl bg-accent-strong px-3 py-2.5 text-sm font-semibold text-white"
            >
              削除する
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
