import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { CompanyDetail, QuestionResponse } from "@/lib/types";

interface Props {
  activeQuestionId: string | null;
  onSelectQuestion: (question: QuestionResponse, companyName: string) => void;
}

export function Sidebar({ activeQuestionId, onSelectQuestion }: Props) {
  const [companies, setCompanies] = useState<CompanyDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [addingCompany, setAddingCompany] = useState(false);
  const [companyName, setCompanyName] = useState("");
  const [questionDraftFor, setQuestionDraftFor] = useState<string | null>(null);
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
      setError(e instanceof ApiError ? e.message : "会社一覧を取得できませんでした");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const createCompany = async () => {
    const name = companyName.trim();
    if (!name) return;
    try {
      const created = await api.createCompany(name);
      setCompanies((prev) => [...prev, created]);
      setCompanyName("");
      setAddingCompany(false);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "会社を追加できませんでした");
    }
  };

  const addQuestion = async (companyId: string) => {
    const text = questionText.trim();
    if (!text) return;
    try {
      const created = await api.addQuestion(companyId, text);
      setCompanies((prev) =>
        prev.map((c) =>
          c.id === companyId
            ? { ...c, questions: [...c.questions, created] }
            : c,
        ),
      );
      setQuestionText("");
      setQuestionDraftFor(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "質問を追加できませんでした");
    }
  };

  const removeQuestion = async (companyId: string, questionId: string) => {
    if (!window.confirm("この質問を削除しますか？")) return;
    try {
      await api.deleteQuestion(questionId);
      setCompanies((prev) =>
        prev.map((c) =>
          c.id === companyId
            ? { ...c, questions: c.questions.filter((q) => q.id !== questionId) }
            : c,
        ),
      );
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "質問を削除できませんでした");
    }
  };

  return (
    <aside className="flex h-full w-72 flex-shrink-0 flex-col border-r border-neutral-200 bg-neutral-50">
      <div className="px-5 pb-3 pt-5">
        <div className="text-lg font-extrabold tracking-tight text-neutral-800">
          mensetu ai
        </div>
        <div className="mt-1 text-xs text-neutral-500">
          練習する質問を選択してください
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-3 pb-4">
        {loading && (
          <p className="px-2 py-3 text-sm text-neutral-400">読み込み中...</p>
        )}
        {error && (
          <p className="px-2 py-3 text-sm text-red-500" role="alert">
            {error}
          </p>
        )}

        {!loading && companies.length === 0 && (
          <p className="px-2 py-3 text-sm text-neutral-400">
            まだ会社がありません。下のボタンから追加してください。
          </p>
        )}

        {companies.map((company) => (
          <div key={company.id} className="mb-4">
            <div className="flex items-center justify-between px-2 pb-1">
              <span className="text-[13.5px] font-bold text-neutral-700">
                {company.name}
              </span>
              <button
                type="button"
                title="質問を追加"
                onClick={() =>
                  setQuestionDraftFor(
                    questionDraftFor === company.id ? null : company.id,
                  )
                }
                className="px-1 text-lg leading-none text-neutral-400 hover:text-violet-600"
              >
                +
              </button>
            </div>

            {company.questions.map((q) => (
              <div key={q.id} className="group flex items-center">
                <button
                  type="button"
                  onClick={() => onSelectQuestion(q, company.name)}
                  className={`flex-1 truncate rounded-lg px-2 py-1.5 text-left text-[13px] ${
                    activeQuestionId === q.id
                      ? "bg-violet-100 font-semibold text-violet-800"
                      : "text-neutral-600 hover:bg-neutral-100"
                  }`}
                >
                  {q.questionText}
                </button>
                <button
                  type="button"
                  title="質問を削除"
                  onClick={() => removeQuestion(company.id, q.id)}
                  className="px-1.5 text-neutral-300 opacity-0 transition group-hover:opacity-100 hover:text-red-500"
                >
                  ×
                </button>
              </div>
            ))}

            {questionDraftFor === company.id && (
              <div className="mt-1 flex gap-1 px-2">
                <input
                  autoFocus
                  value={questionText}
                  onChange={(e) => setQuestionText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") void addQuestion(company.id);
                  }}
                  placeholder="質問文を入力"
                  className="min-w-0 flex-1 rounded-md border border-neutral-300 px-2 py-1 text-xs outline-none focus:border-violet-400"
                />
                <button
                  type="button"
                  onClick={() => void addQuestion(company.id)}
                  className="rounded-md bg-violet-600 px-2 py-1 text-xs font-semibold text-white"
                >
                  追加
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="border-t border-neutral-200 p-3">
        {addingCompany ? (
          <div className="flex gap-1">
            <input
              autoFocus
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") void createCompany();
                if (e.key === "Escape") setAddingCompany(false);
              }}
              placeholder="会社名"
              className="min-w-0 flex-1 rounded-md border border-neutral-300 px-2 py-1.5 text-sm outline-none focus:border-violet-400"
            />
            <button
              type="button"
              onClick={() => void createCompany()}
              className="rounded-md bg-violet-600 px-3 py-1.5 text-sm font-semibold text-white"
            >
              追加
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setAddingCompany(true)}
            className="w-full rounded-lg border border-dashed border-neutral-300 px-2 py-2 text-sm font-semibold text-neutral-600 hover:border-violet-400 hover:text-violet-700"
          >
            ＋ 会社を追加
          </button>
        )}
      </div>
    </aside>
  );
}
