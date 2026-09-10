import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MockSummaryScreen } from "@/components/chat/MockSummaryScreen";
import type { MockReportResponse, MockSessionSummary } from "@/lib/types";

const report: MockReportResponse = {
  sessionId: "s1",
  score: 78,
  answerTendencyAnalysis: "結論から話す意識はあるが、具体性がやや浅い。",
  feedbacks: [
    {
      questionText: "自己紹介をお願いします",
      userAnswerSummary: "ゼミ活動について話した",
      feedback: "経歴の説明にとどまっていた。",
      sequenceNo: 0,
    },
  ],
  createdAt: "",
};

function renderScreen(options: {
  report?: MockReportResponse | null;
  loading?: boolean;
  error?: string | null;
  pastSessions?: MockSessionSummary[];
  onRetryInterview?: () => void;
  onBackToList?: () => void;
} = {}) {
  return render(
    <MockSummaryScreen
      companyName="ABC商事"
      questionText="自己紹介をお願いします"
      report={options.report ?? null}
      loading={options.loading ?? false}
      error={options.error ?? null}
      pastSessions={options.pastSessions ?? []}
      onRetryInterview={options.onRetryInterview ?? (() => {})}
      onBackToList={options.onBackToList ?? (() => {})}
    />,
  );
}

describe("MockSummaryScreen", () => {
  it("採点中はローディング表示のみでレポート本文は出さない", () => {
    renderScreen({ loading: true });

    expect(screen.getByText("採点中です...")).toBeTruthy();
    expect(screen.queryByText(/100点/)).toBeNull();
  });

  it("レポートがあればスコア・傾向分析・質問ごとのフィードバックを表示する", () => {
    renderScreen({ report });

    expect(screen.getByText("78")).toBeTruthy();
    expect(screen.getByText("結論から話す意識はあるが、具体性がやや浅い。")).toBeTruthy();
    expect(screen.getByText("自己紹介をお願いします")).toBeTruthy();
    expect(screen.getByText("経歴の説明にとどまっていた。")).toBeTruthy();
  });

  it("エラーがあれば表示する", () => {
    renderScreen({ error: "レポートを取得できませんでした" });

    expect(screen.getByRole("alert").textContent).toBe("レポートを取得できませんでした");
  });

  it("もう一度挑戦する/会社一覧に戻るのコールバックが呼ばれる", () => {
    const onRetryInterview = vi.fn();
    const onBackToList = vi.fn();
    renderScreen({ report, onRetryInterview, onBackToList });

    fireEvent.click(screen.getByRole("button", { name: "もう一度挑戦する" }));
    fireEvent.click(screen.getByRole("button", { name: "会社一覧に戻る" }));

    expect(onRetryInterview).toHaveBeenCalledTimes(1);
    expect(onBackToList).toHaveBeenCalledTimes(1);
  });

  it("過去の挑戦が2件以上あれば一覧を表示する", () => {
    renderScreen({
      report,
      pastSessions: [
        { id: "s1", status: "ENDED", endedReason: "AI_JUDGED", score: 78, startedAt: "", endedAt: "2026-01-01T00:00:00Z", createdAt: "" },
        { id: "s0", status: "ENDED", endedReason: "USER_ENDED", score: 60, startedAt: "", endedAt: "2025-12-01T00:00:00Z", createdAt: "" },
      ],
    });

    expect(screen.getByText("これまでの挑戦")).toBeTruthy();
    expect(screen.getByText("60点")).toBeTruthy();
  });

  it("過去の挑戦が1件以下なら一覧を表示しない", () => {
    renderScreen({
      report,
      pastSessions: [
        { id: "s1", status: "ENDED", endedReason: "AI_JUDGED", score: 78, startedAt: "", endedAt: "", createdAt: "" },
      ],
    });

    expect(screen.queryByText("これまでの挑戦")).toBeNull();
  });
});
