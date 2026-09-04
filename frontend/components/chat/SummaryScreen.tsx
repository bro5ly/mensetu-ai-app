import type { PracticeEndResponse } from "@/lib/types";

interface Props {
  summary: PracticeEndResponse | null;
  onRestart: () => void;
}

export function SummaryScreen({ summary, onRestart }: Props) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-10 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-violet-600 text-3xl font-bold text-white">
        ✓
      </div>
      <div className="text-xl font-bold text-neutral-800">お疲れ様でした</div>
      <p className="max-w-sm text-sm leading-relaxed text-neutral-500">
        {summary?.lightSummary ??
          "練習を終了しました。またいつでも続きから再開できます。"}
      </p>
      <button
        type="button"
        onClick={onRestart}
        className="mt-2 rounded-full bg-violet-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-violet-700"
      >
        質問の選択に戻る
      </button>
    </div>
  );
}
