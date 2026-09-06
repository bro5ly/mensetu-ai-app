export type MessageRole = "USER" | "ASSISTANT";
export type MessageType = "NORMAL" | "ADVICE";
export type SessionStatus = "READY" | "IN_PROGRESS" | "ENDED";

export interface CompanySummary {
  id: string;
  name: string;
  createdAt: string;
}

export interface QuestionResponse {
  id: string;
  companyId: string;
  questionText: string;
  internalCategory: string | null;
  displayOrder: number;
  createdAt: string;
}

export interface CompanyDetail {
  id: string;
  name: string;
  overview: string | null;
  createdAt: string;
  updatedAt: string;
  questions: QuestionResponse[];
  sources?: SourceResponse[];
}

/** 企業リサーチ: ユーザーが登録したURLをfetchした結果の下書き(未保存)。 */
export interface FetchedSourcePreview {
  url: string;
  title: string | null;
  content: string;
}

/** 会社に紐づく保存済みソース。 */
export interface SourceResponse {
  id: string;
  companyId: string;
  url: string;
  title: string | null;
  content: string;
  fetchedAt: string;
  createdAt: string;
}

/** URL一括追加のうち失敗した1件。 */
export interface FailedSource {
  url: string;
  message: string;
}

/** ソース一括追加の結果(成功/失敗を分けて返す)。 */
export interface BatchAddSourcesResponse {
  added: SourceResponse[];
  failed: FailedSource[];
}

export interface MessageResponse {
  id: string;
  role: MessageRole;
  content: string;
  messageType: MessageType;
  sequenceNo: number;
  createdAt: string;
}

export interface SessionDetail {
  id: string;
  companyId: string;
  questionId: string | null;
  mode: "PRACTICE" | "MOCK";
  status: SessionStatus;
  endedReason: string | null;
  lightSummary: string | null;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
  messages: MessageResponse[];
}

export interface PracticeSessionResponse {
  id: string;
  companyId: string;
  questionId: string;
  status: SessionStatus;
  resumed: boolean;
  createdAt: string;
}

export interface PracticeEndResponse {
  sessionId: string;
  messageCount: number;
  lightSummary: string | null;
}

/**
 * 企業リサーチ: 未保存の企業概要下書き。
 * `sources` はリクエストで渡したソースに、SearXNGで自動的に見つかったソースを加えた
 * 最終的な一覧(ユーザー提供分が先頭)。
 */
export interface ResearchDraft {
  overview: string;
  sources: FetchedSourcePreview[];
}

/** 企業リサーチ: 未保存の生成済み質問。 */
export interface GeneratedQuestions {
  questions: string[];
}

/** チャット表示用のメッセージ（ストリーミング中の未確定分も含む）。 */
export interface ChatMessage {
  id: string;
  role: MessageRole;
  messageType: MessageType;
  content: string;
  streaming: boolean;
}
