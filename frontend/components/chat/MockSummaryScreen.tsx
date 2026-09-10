import type { MockReportResponse, MockSessionSummary } from "@/lib/types";

interface Props {
  companyName: string;
  questionText: string;
  report: MockReportResponse | null;
  loading: boolean;
  error: string | null;
  pastSessions: MockSessionSummary[];
  onRetryInterview: () => void;
  onBackToList: () => void;
}

function scoreColor(score: number): string {
  if (score >= 80) return "text-accent";
  if (score >= 50) return "text-ink";
  return "text-accent-strong";
}

export function MockSummaryScreen({
  companyName,
  questionText,
  report,
  loading,
  error,
  pastSessions,
  onRetryInterview,
  onBackToList,
}: Props) {
  return (
    <div className="flex h-full flex-col items-center overflow-y-auto p-10">
      <div className="flex w-full max-w-xl flex-col items-center gap-[18px] text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-[28px] font-bold text-white">
          ✓
        </div>
        <div className="text-[22px] font-bold text-ink">お疲れ様でした</div>
        <p className="text-sm leading-[1.6] text-ink-soft">
          {companyName}の「{questionText}」の本番模擬面接が終了しました。
        </p>

        {loading && (
          <div className="flex items-center gap-1.5 py-6">
            {[0, 0.15, 0.3].map((delay) => (
              <span
                key={delay}
                className="h-1.5 w-1.5 rounded-full bg-ink-soft"
                style={{ animation: "bounceDot 1.2s infinite", animationDelay: `${delay}s` }}
              />
            ))}
            <span className="ml-2 text-[13px] text-ink-faint">採点中です...</span>
          </div>
        )}

        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600">
            {error}
          </p>
        )}

        {report && (
          <div className="w-full text-left">
            <div className="flex items-center justify-center gap-2 py-4">
              <span className={`text-[44px] font-extrabold ${scoreColor(report.score)}`}>
                {report.score}
              </span>
              <span className="text-[15px] font-semibold text-ink-soft">/ 100点</span>
            </div>

            <div className="mb-5 rounded-2xl bg-panel-bubble p-4">
              <div className="mb-1.5 text-[12px] font-bold uppercase tracking-[0.04em] text-ink-soft">
                回答の傾向
              </div>
              <p className="whitespace-pre-wrap text-[14px] leading-[1.6] text-ink">
                {report.answerTendencyAnalysis}
              </p>
            </div>

            <div className="flex flex-col gap-3">
              {report.feedbacks.map((f, i) => (
                <div key={i} className="rounded-2xl border border-line p-4">
                  <div className="mb-1.5 text-[13px] font-semibold text-ink">{f.questionText}</div>
                  {f.userAnswerSummary && (
                    <p className="mb-2 text-[12.5px] leading-[1.5] text-ink-faint">
                      回答: {f.userAnswerSummary}
                    </p>
                  )}
                  <p className="text-[13px] leading-[1.6] text-ink-soft">{f.feedback}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {pastSessions.length > 1 && (
          <div className="mt-2 w-full">
            <div className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-ink-soft">
              これまでの挑戦
            </div>
            <div className="flex flex-col gap-1.5">
              {pastSessions.map((s) => (
                <div
                  key={s.id}
                  className="flex items-center justify-between rounded-lg border border-line px-3.5 py-2 text-[12.5px]"
                >
                  <span className="text-ink-faint">
                    {s.endedAt ? new Date(s.endedAt).toLocaleDateString("ja-JP") : "進行中"}
                  </span>
                  <span className="font-semibold text-ink">
                    {s.score != null ? `${s.score}点` : "採点待ち"}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-2 flex w-full max-w-[260px] flex-col gap-2.5">
          <button
            type="button"
            onClick={onRetryInterview}
            className="rounded-[14px] bg-accent px-3 py-[13px] text-[14.5px] font-semibold text-white"
          >
            もう一度挑戦する
          </button>
          <button
            type="button"
            onClick={onBackToList}
            className="rounded-[14px] border border-line bg-white px-3 py-[13px] text-[14.5px] font-semibold text-ink"
          >
            会社一覧に戻る
          </button>
        </div>
      </div>
    </div>
  );
}
