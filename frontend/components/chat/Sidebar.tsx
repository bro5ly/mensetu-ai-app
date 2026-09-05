import { useCallback, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { Modal } from "@/components/ui/Modal";
import { AddCompanyWizard } from "./AddCompanyWizard";
import type { CompanyDetail, QuestionResponse } from "@/lib/types";

interface Props {
  activeQuestionId: string | null;
  onSelectQuestion: (question: QuestionResponse, companyName: string) => void;
  /** この値が変わると会社一覧を再取得する（チャット画面での質問削除後など） */
  reloadSignal?: number;
}

/** ui-design のアバター配色（hue 295 固定、明度だけ会社ごとに変える） */
const AVATAR_LIGHTNESS = [0.55, 0.62, 0.48, 0.58, 0.51];

export function Sidebar({
  activeQuestionId,
  onSelectQuestion,
  reloadSignal = 0,
}: Props) {
  const [companies, setCompanies] = useState<CompanyDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  const [showWizard, setShowWizard] = useState(false);
  const [addQuestionFor, setAddQuestionFor] = useState<CompanyDetail | null>(
    null,
  );
  const [questionText, setQuestionText] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const summaries = await api.listCompanies();
      const details = await Promise.all(
        summaries.map((c) => api.getCompany(c.id)),
      );
      setCompanies(details);
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : "会社一覧を取得できませんでした",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, reloadSignal]);

  const toggleCompany = (id: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const handleCompanyCreated = (created: CompanyDetail) => {
    setCompanies((prev) => [...prev, created]);
    setShowWizard(false);
    if (created.questions[0]) {
      onSelectQuestion(created.questions[0], created.name);
    }
  };

  const addQuestion = async () => {
    const company = addQuestionFor;
    const text = questionText.trim();
    if (!company || !text) return;
    try {
      const created = await api.addQuestion(company.id, text);
      setCompanies((prev) =>
        prev.map((c) =>
          c.id === company.id
            ? { ...c, questions: [...c.questions, created] }
            : c,
        ),
      );
      setQuestionText("");
      setAddQuestionFor(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "質問を追加できませんでした");
    }
  };

  const emptyState = useMemo(
    () => !loading && companies.length === 0,
    [loading, companies.length],
  );

  return (
    <>
      <aside className="flex h-full w-[280px] flex-shrink-0 flex-col border-r border-line bg-panel-sidebar">
        <div className="flex-1 overflow-y-auto pb-3 pt-5">
          <div className="px-5 pb-4">
            <div className="text-[18px] font-extrabold tracking-[-0.02em] text-ink">
              mensetu ai
            </div>
            <div className="mt-[3px] text-xs text-ink-soft">
              AIコーチと練習する質問を選択
            </div>
          </div>

          <button
            type="button"
            onClick={() => setShowWizard(true)}
            className="mx-3 mb-4 flex w-[calc(100%-24px)] items-center gap-2.5 rounded-lg px-2 py-1.5 text-left text-[13.5px] font-semibold text-ink transition hover:bg-panel-hover"
          >
            <span className="flex h-[26px] w-[26px] flex-shrink-0 items-center justify-center rounded-[7px] border border-line bg-white text-[15px] text-accent">
              +
            </span>
            <span>会社を追加</span>
          </button>

          <div className="px-5 pb-1 pt-1.5 text-[10px] font-bold uppercase tracking-[0.06em] text-ink-faint">
            会社
          </div>

          {loading && (
            <p className="px-5 py-3 text-sm text-ink-faint">読み込み中...</p>
          )}
          {error && (
            <p className="px-5 py-3 text-sm text-red-500" role="alert">
              {error}
            </p>
          )}
          {emptyState && (
            <p className="px-5 py-3 text-sm text-ink-faint">
              まだ会社がありません。上の「会社を追加」から登録してください。
            </p>
          )}

          {companies.map((company, index) => {
            const isOpen = !collapsed.has(company.id);
            const lightness =
              AVATAR_LIGHTNESS[index % AVATAR_LIGHTNESS.length];
            return (
              <div key={company.id} className="mb-2">
                <div className="flex items-center gap-2 py-1 pl-5 pr-4">
                  <span
                    className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-[7px] text-[12px] font-bold text-white"
                    style={{ background: `oklch(${lightness} 0.12 295)` }}
                  >
                    {company.name.charAt(0)}
                  </span>
                  <button
                    type="button"
                    onClick={() => toggleCompany(company.id)}
                    className="flex min-w-0 flex-1 items-center justify-between gap-2 text-left"
                    aria-expanded={isOpen}
                  >
                    <span className="truncate text-[13.5px] font-bold text-ink">
                      {company.name}
                    </span>
                    <svg
                      width="10"
                      height="10"
                      viewBox="0 0 10 10"
                      className={`flex-shrink-0 text-ink-soft transition-transform ${
                        isOpen ? "" : "-rotate-90"
                      }`}
                      aria-hidden
                    >
                      <path
                        d="M1 3l4 4 4-4"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.6"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </button>
                  <button
                    type="button"
                    title="質問を追加"
                    onClick={() => {
                      setQuestionText("");
                      setAddQuestionFor(company);
                    }}
                    className="flex h-5 w-5 flex-shrink-0 items-center justify-center text-[17px] leading-none text-ink-soft hover:text-accent"
                  >
                    +
                  </button>
                </div>

                {isOpen &&
                  company.questions.map((q) => {
                    const active = activeQuestionId === q.id;
                    return (
                      <button
                        key={q.id}
                        type="button"
                        onClick={() => onSelectQuestion(q, company.name)}
                        className={`my-px flex w-full items-baseline gap-2 py-2 pl-6 pr-5 text-left text-[12.5px] leading-[1.4] transition ${
                          active
                            ? "bg-accent-surface font-semibold text-accent-ink"
                            : "font-normal text-ink-soft hover:bg-panel-hover"
                        }`}
                      >
                        <span
                          className={`mt-1.5 h-1 w-1 flex-shrink-0 rounded-full ${
                            active ? "bg-accent" : "bg-[oklch(0.75_0.01_60)]"
                          }`}
                        />
                        <span>{q.questionText}</span>
                      </button>
                    );
                  })}

                {isOpen && company.questions.length === 0 && (
                  <p className="py-1.5 pl-6 pr-5 text-[12px] text-ink-faint">
                    質問がまだありません
                  </p>
                )}
              </div>
            );
          })}
        </div>

        <div className="flex flex-shrink-0 items-center gap-2.5 border-t border-line px-4 py-[18px]">
          <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-accent text-[12.5px] font-bold text-white">
            ユ
          </span>
          <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-ink">
            ユーザー
          </span>
          <button
            type="button"
            title="設定"
            className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full transition hover:bg-panel-hover"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="oklch(0.5 0.008 60)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden
            >
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
          </button>
        </div>
      </aside>

      {showWizard && (
        <AddCompanyWizard
          onClose={() => setShowWizard(false)}
          onCreated={handleCompanyCreated}
        />
      )}

      {addQuestionFor && (
        <Modal
          onClose={() => setAddQuestionFor(null)}
          maxWidth={340}
          labelledBy="add-question-title"
        >
          <div className="mb-1 text-[11px] font-bold uppercase tracking-[0.04em] text-ink-soft">
            {addQuestionFor.name}に質問を追加
          </div>
          <div
            id="add-question-title"
            className="mb-3.5 text-[16px] font-bold text-ink"
          >
            質問を追加
          </div>
          <input
            autoFocus
            value={questionText}
            onChange={(e) => setQuestionText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") void addQuestion();
            }}
            placeholder="例：あなたの長所と短所を教えてください"
            className="mb-4 w-full rounded-[10px] border border-line px-3.5 py-2.5 text-sm outline-none focus:border-accent"
          />
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setAddQuestionFor(null)}
              className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
            >
              キャンセル
            </button>
            <button
              type="button"
              onClick={() => void addQuestion()}
              disabled={!questionText.trim()}
              className="flex-1 rounded-xl px-3 py-2.5 text-sm font-semibold text-white transition enabled:bg-accent disabled:cursor-not-allowed disabled:bg-panel-muted disabled:text-ink-faint"
            >
              追加する
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
