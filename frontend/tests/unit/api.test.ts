import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api";

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

function jsonResponse(body: unknown, init: Partial<Response> = {}) {
  return {
    ok: init.status ? init.status < 400 : true,
    status: init.status ?? 200,
    statusText: init.statusText ?? "OK",
    json: async () => body,
  } as Response;
}

describe("api", () => {
  it("会社一覧をGETする", async () => {
    fetchMock.mockResolvedValue(jsonResponse([{ id: "c1", name: "ABC社" }]));

    const companies = await api.listCompanies();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/companies",
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(companies).toHaveLength(1);
  });

  it("練習セッション開始はPOSTする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "s1" }));

    await api.startPracticeSession("q1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/questions/q1/practice-session",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("企業リサーチはnameをPOSTする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ overview: "概要テキスト" }));

    const draft = await api.researchCompany({ name: "ABC商事" });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/companies/research",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "ABC商事" }),
      }),
    );
    expect(draft.overview).toBe("概要テキスト");
  });

  it("質問生成はnameとoverviewをPOSTする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ questions: ["Q1", "Q2", "Q3"] }));

    const result = await api.generateQuestions("ABC商事", "概要");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/companies/generate-questions",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "ABC商事", overview: "概要" }),
      }),
    );
    expect(result.questions).toHaveLength(3);
  });

  it("会社作成はname/overview/questionsをPOSTする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "c1", questions: [] }));

    await api.createCompany({
      name: "ABC商事",
      overview: "概要",
      questions: ["Q1", "Q2"],
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/companies",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          name: "ABC商事",
          overview: "概要",
          questions: ["Q1", "Q2"],
        }),
      }),
    );
  });

  it("エラーレスポンスのmessageをApiErrorに変換する", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ message: "会社が見つかりません" }, { status: 404 }),
    );

    await expect(api.getCompany("missing")).rejects.toMatchObject({
      name: "ApiError",
      status: 404,
      message: "会社が見つかりません",
    });
  });

  it("本番セッション開始はquestionIdへPOSTする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "s1", questions: [] }));

    await api.startMockSession("q1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/questions/q1/mock-sessions",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("本番セッション一覧はquestionIdでGETする", async () => {
    fetchMock.mockResolvedValue(jsonResponse([{ id: "s1", score: 80 }]));

    const sessions = await api.listMockSessions("q1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/questions/q1/mock-sessions",
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(sessions).toHaveLength(1);
  });

  it("本番セッション終了はsessionIdへPOSTする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ sessionId: "s1", status: "REPORT_PENDING" }));

    const result = await api.endMockSession("s1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/sessions/s1/end",
      expect.objectContaining({ method: "POST" }),
    );
    expect(result.status).toBe("REPORT_PENDING");
  });

  it("本番レポート取得はsessionIdでGETする", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ sessionId: "s1", score: 80, feedbacks: [] }));

    const report = await api.getMockReport("s1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/sessions/s1/report",
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(report.score).toBe(80);
  });

  it("204はundefinedを返す", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => {
        throw new Error("no body");
      },
    } as unknown as Response);

    await expect(api.deleteQuestion("q1")).resolves.toBeUndefined();
    expect(ApiError).toBeDefined();
  });
});
