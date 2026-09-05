import { API_BASE_URL } from "./config";
import type {
  CompanyDetail,
  CompanySummary,
  GeneratedQuestions,
  PracticeEndResponse,
  PracticeSessionResponse,
  QuestionResponse,
  ResearchDraft,
  SessionDetail,
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
  }) =>
    request<CompanyDetail>("/api/companies", {
      method: "POST",
      body: JSON.stringify(input),
    }),

  deleteCompany: (companyId: string) =>
    request<void>(`/api/companies/${companyId}`, { method: "DELETE" }),

  researchCompany: (body: {
    name: string;
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
