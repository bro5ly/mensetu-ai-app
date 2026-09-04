import type { PracticeEndResponse } from "@/lib/types";

interface Props {
  summary: PracticeEndResponse | null;
  turnCount: number;
  onPracticeAgain: () => void;
  onBackToList: () => void;
}

export function SummaryScreen({
  summary,
  turnCount,
  onPracticeAgain,
  onBackToList,
}: Props) {
  const body =
    summary?.lightSummary ??
    `今回の練習では、AIコーチと${turnCount}回のやり取りをしました。`;

  return (
    <div className="flex h-full flex-col items-center justify-center gap-[18px] p-10 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-[28px] font-bold text-white">
        ✓
      </div>
      <div className="text-[22px] font-bold text-ink">お疲れ様でした</div>
      <p className="max-w-sm text-sm leading-[1.6] text-ink-soft">{body}</p>
      <div className="mt-2 flex w-full max-w-[260px] flex-col gap-2.5">
        <button
          type="button"
          onClick={onPracticeAgain}
          className="rounded-[14px] bg-accent px-3 py-[13px] text-[14.5px] font-semibold text-white"
        >
          もう一度練習する
        </button>
        <button
          type="button"
          onClick={onBackToList}
          className="rounded-[14px] border border-line bg-white px-3 py-[13px] text-[14.5px] font-semibold text-ink"
        >
          質問一覧に戻る
        </button>
      </div>
    </div>
  );
}
