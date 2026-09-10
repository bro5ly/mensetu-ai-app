import { API_BASE_URL } from "./config";
import type {
  BatchAddSourcesResponse,
  CompanyDetail,
  CompanySummary,
  FetchedSourcePreview,
  GeneratedQuestions,
  MockEndResponse,
  MockReportResponse,
  MockSessionStartResponse,
  MockSessionSummary,
  PracticeEndResponse,
  PracticeSessionResponse,
  QuestionResponse,
  ResearchDraft,
  SessionDetail,
  SourceResponse,
  UserProfileResponse,
} from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = (await res.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // レスポンスボディが JSON でない場合はステータステキストのまま
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  listCompanies: () => request<CompanySummary[]>("/api/companies"),

  getCompany: (companyId: string) =>
    request<CompanyDetail>(`/api/companies/${companyId}`),

  /** 会社に紐づかない「汎用的な質問」。サイドバーの「会社」セクションの上に表示する。 */
  getGenericQuestions: () => request<CompanyDetail>("/api/companies/generic"),

  createCompany: (input: {
    name: string;
    overview?: string;
    questions?: string[];
    sources?: FetchedSourcePreview[];
  }) =>
    request<CompanyDetail>("/api/companies", {
      method: "POST",
      body: JSON.stringify(input),
    }),

  deleteCompany: (companyId: string) =>
    request<void>(`/api/companies/${companyId}`, { method: "DELETE" }),

  researchCompany: (body: {
    name: string;
    sources?: FetchedSourcePreview[];
    currentOverview?: string;
    feedback?: string;
  }) =>
    request<ResearchDraft>("/api/companies/research", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  generateQuestions: (name: string, overview: string) =>
    request<GeneratedQuestions>("/api/companies/generate-questions", {
      method: "POST",
      body: JSON.stringify({ name, overview }),
    }),

  fetchSourcePreview: (url: string) =>
    request<FetchedSourcePreview>("/api/companies/sources/fetch", {
      method: "POST",
      body: JSON.stringify({ url }),
    }),

  listSources: (companyId: string) =>
    request<SourceResponse[]>(`/api/companies/${companyId}/sources`),

  addSource: (companyId: string, url: string) =>
    request<SourceResponse>(`/api/companies/${companyId}/sources`, {
      method: "POST",
      body: JSON.stringify({ url }),
    }),

  addSources: (companyId: string, urls: string[]) =>
    request<BatchAddSourcesResponse>(`/api/companies/${companyId}/sources/batch`, {
      method: "POST",
      body: JSON.stringify({ urls }),
    }),

  deleteSource: (sourceId: string) =>
    request<void>(`/api/sources/${sourceId}`, { method: "DELETE" }),

  addQuestion: (companyId: string, questionText: string) =>
    request<QuestionResponse>(`/api/companies/${companyId}/questions`, {
      method: "POST",
      body: JSON.stringify({ questionText }),
    }),

  deleteQuestion: (questionId: string) =>
    request<void>(`/api/questions/${questionId}`, { method: "DELETE" }),

  startPracticeSession: (questionId: string) =>
    request<PracticeSessionResponse>(
      `/api/questions/${questionId}/practice-session`,
      { method: "POST" },
    ),

  getSession: (sessionId: string) =>
    request<SessionDetail>(`/api/sessions/${sessionId}`),

  endSession: (sessionId: string) =>
    request<PracticeEndResponse>(`/api/sessions/${sessionId}/end`, {
      method: "POST",
    }),

  startMockSession: (questionId: string) =>
    request<MockSessionStartResponse>(`/api/questions/${questionId}/mock-sessions`, {
      method: "POST",
    }),

  listMockSessions: (questionId: string) =>
    request<MockSessionSummary[]>(`/api/questions/${questionId}/mock-sessions`),

  /** MOCKセッションの強制終了。202が返り、レポート生成は非同期(getMockReportで取得)。 */
  endMockSession: (sessionId: string) =>
    request<MockEndResponse>(`/api/sessions/${sessionId}/end`, { method: "POST" }),

  /** レポートが未準備(生成中含む)の間は404(ApiErrorのstatus=404)になる。 */
  getMockReport: (sessionId: string) =>
    request<MockReportResponse>(`/api/sessions/${sessionId}/report`),

  getUserProfile: () => request<UserProfileResponse>("/api/profile"),

  updateUserProfile: (resumeText: string) =>
    request<UserProfileResponse>("/api/profile", {
      method: "PUT",
      body: JSON.stringify({ resumeText }),
    }),
};
