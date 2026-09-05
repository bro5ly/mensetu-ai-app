import { API_BASE_URL } from "./config";
import type {
  CompanyDetail,
  CompanySummary,
  FetchedSourcePreview,
  GeneratedQuestions,
  PracticeEndResponse,
  PracticeSessionResponse,
  QuestionResponse,
  ResearchDraft,
  SessionDetail,
  SourceResponse,
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
};
