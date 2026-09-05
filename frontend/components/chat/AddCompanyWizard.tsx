import { Modal } from "@/components/ui/Modal";
import { useAddCompanyWizard } from "@/hooks/useAddCompanyWizard";
import type { CompanyDetail } from "@/lib/types";

interface Props {
  onClose: () => void;
  onCreated: (company: CompanyDetail) => void;
}

const STEP_LABELS = ["会社名", "内容の確認", "質問作成"] as const;

function Spinner({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center gap-1.5 py-6">
      {[0, 0.15, 0.3].map((d) => (
        <span
          key={d}
          className="h-1.5 w-1.5 rounded-full bg-ink-soft"
          style={{ animation: "bounceDot 1.2s infinite", animationDelay: `${d}s` }}
        />
      ))}
      <span className="ml-2 text-[13px] text-ink-soft">{label}</span>
    </div>
  );
}

export function AddCompanyWizard({ onClose, onCreated }: Props) {
  const w = useAddCompanyWizard();
  const { state, stepIndex } = w;

  const finalize = async () => {
    const created = await w.finalize();
    if (created) {
      w.reset();
      onCreated(created);
    }
  };

  return (
    <Modal onClose={onClose} maxWidth={520} labelledBy="add-company-title">
      <div className="mb-[18px] flex items-center justify-between">
        <div id="add-company-title" className="text-[16px] font-bold text-ink">
          会社を追加
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="閉じる"
          className="text-[20px] leading-none text-ink-soft"
        >
          ×
        </button>
      </div>

      {/* ステッパー */}
      <div className="mb-5 flex gap-1.5">
        {STEP_LABELS.map((label, i) => (
          <div key={label} className="flex-1">
            <div
              className={`h-[3px] rounded-sm ${
                i <= stepIndex ? "bg-accent" : "bg-panel-muted"
              }`}
            />
            <div
              className={`mt-1.5 text-[10.5px] ${
                i === stepIndex ? "font-bold" : "font-medium"
              } ${i <= stepIndex ? "text-accent-ink" : "text-ink-faint"}`}
            >
              {label}
            </div>
          </div>
        ))}
      </div>

      {/* Step: 会社名 */}
      {state.step === "name" && (
        <>
          <p className="mb-2.5 text-[13px] leading-relaxed text-ink-soft">
            会社名を入力してください。AIが企業の特徴や面接情報を検索します。
          </p>
          <input
            autoFocus
            value={state.name}
            onChange={(e) => w.setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") void w.search();
            }}
            placeholder="例：ABCコーポレーション"
            className="mb-4 w-full rounded-[10px] border border-line px-3.5 py-2.5 text-sm outline-none focus:border-accent"
          />
          {state.busy === "search" ? (
            <Spinner label="企業情報を検索中..." />
          ) : (
            <button
              type="button"
              onClick={() => void w.search()}
              disabled={!w.canSearch}
              className="w-full rounded-xl py-3 text-sm font-semibold text-white transition enabled:bg-accent disabled:cursor-not-allowed disabled:bg-panel-muted disabled:text-ink-faint"
            >
              検索する
            </button>
          )}
        </>
      )}

      {/* Step: 内容の確認 */}
      {state.step === "review" && (
        <>
          <div
            className={`mb-3.5 rounded-[14px] p-4 ${
              state.confirmed
                ? "border-[1.5px] border-accent-border bg-accent-surface"
                : "border border-line bg-panel-sidebar"
            }`}
          >
            <div className="mb-2.5 flex items-center justify-between gap-2">
              <span className="text-[12.5px] font-bold text-ink">
                {state.name}の特徴・面接傾向
              </span>
              {state.confirmed && (
                <span className="flex-shrink-0 rounded-full bg-accent-surface px-2 py-0.5 text-[11px] font-bold text-accent-ink">
                  ✓ 決定済み
                </span>
              )}
            </div>

            {state.editing ? (
              <textarea
                rows={7}
                value={state.overview}
                onChange={(e) => w.setOverview(e.target.value)}
                className="w-full resize-y rounded-[10px] border border-line p-3 text-[13px] leading-relaxed outline-none focus:border-accent"
              />
            ) : (
              <div className="whitespace-pre-wrap text-[13px] leading-relaxed text-ink">
                {state.overview}
              </div>
            )}

            {state.busy === "research" && <Spinner label="再検索中..." />}

            {!state.confirmed &&
              state.busy !== "research" &&
              !state.researchOpen && (
                <div className="mt-3 flex items-center gap-2">
                  <button
                    type="button"
                    onClick={w.toggleEdit}
                    className="rounded-lg border border-line bg-white px-3 py-1.5 text-[12px] font-semibold text-ink"
                  >
                    {state.editing ? "保存する" : "編集する"}
                  </button>
                  <button
                    type="button"
                    onClick={w.openResearch}
                    className="rounded-lg border border-line bg-white px-3 py-1.5 text-[12px] font-semibold text-ink"
                  >
                    再検索する
                  </button>
                  <div className="flex-1" />
                  <button
                    type="button"
                    onClick={w.confirm}
                    className="rounded-lg bg-accent px-3.5 py-1.5 text-[12px] font-semibold text-white"
                  >
                    決定
                  </button>
                </div>
              )}

            {state.researchOpen && (
              <div className="mt-2.5 border-t border-line pt-2.5">
                <div className="mb-1.5 text-[12px] text-ink-soft">
                  反映してほしい意見があれば入力してください（任意）
                </div>
                <input
                  autoFocus
                  value={state.feedback}
                  onChange={(e) => w.setFeedback(e.target.value)}
                  placeholder="例：もっと社風について詳しく"
                  className="mb-2 w-full rounded-lg border border-line px-3 py-2 text-[12.5px] outline-none focus:border-accent"
                />
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => void w.reSearch()}
                    className="rounded-lg bg-accent px-3.5 py-1.5 text-[12px] font-semibold text-white"
                  >
                    この内容で再検索する
                  </button>
                  <button
                    type="button"
                    onClick={w.cancelResearch}
                    className="text-[12.5px] font-semibold text-ink-soft"
                  >
                    キャンセル
                  </button>
                </div>
              </div>
            )}

            {state.confirmed && (
              <button
                type="button"
                onClick={w.reopen}
                className="mt-2.5 text-[12.5px] font-semibold text-ink-soft"
              >
                編集し直す
              </button>
            )}
          </div>

          {state.confirmed &&
            (state.busy === "generate" ? (
              <Spinner label="質問を作成中..." />
            ) : (
              <button
                type="button"
                onClick={() => void w.generate()}
                className="w-full rounded-xl bg-accent py-3 text-sm font-semibold text-white"
              >
                この内容から質問を作成する
              </button>
            ))}
        </>
      )}

      {/* Step: 質問作成 */}
      {state.step === "questions" && (
        <>
          <div className="mb-2.5 text-[13px] font-bold text-ink">
            AIが作成した質問
          </div>
          <div className="mb-4 flex flex-col gap-2">
            {(state.generated ?? []).map((q, i) => (
              <div
                key={i}
                className="rounded-[10px] bg-panel-bubble px-3.5 py-2.5 text-[13.5px] leading-snug text-ink"
              >
                {q}
              </div>
            ))}
          </div>
          {state.busy === "create" ? (
            <Spinner label="追加しています..." />
          ) : (
            <button
              type="button"
              onClick={() => void finalize()}
              className="w-full rounded-xl bg-accent py-3 text-sm font-semibold text-white"
            >
              この質問をリストに追加する
            </button>
          )}
        </>
      )}

      {state.error && (
        <p className="mt-3 text-[12.5px] text-red-500" role="alert">
          {state.error}
        </p>
      )}
    </Modal>
  );
}
