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

/** 本番模擬面接: 対象の質問(常に1件)。既存のinterview_questionsの文面をそのままコピーしたもの。 */
export interface MockFlowQuestion {
  questionText: string;
  internalCategory: string | null;
  displayOrder: number;
}

/** 本番模擬面接セッション開始のレスポンス(対象の質問を含む)。 */
export interface MockSessionStartResponse {
  id: string;
  companyId: string;
  status: SessionStatus;
  questions: MockFlowQuestion[];
  createdAt: string;
}

/** 過去の本番セッション一覧の1件(スコア推移表示用)。レポート未生成ならscoreはnull。 */
export interface MockSessionSummary {
  id: string;
  status: SessionStatus;
  endedReason: "USER_ENDED" | "AI_JUDGED" | null;
  score: number | null;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

/** 本番モードのユーザー強制終了レスポンス(レポートは非同期生成のため未確定)。 */
export interface MockEndResponse {
  sessionId: string;
  status: string;
}

/** 本番レポート内の質問ごとの具体的フィードバック。 */
export interface MockQuestionFeedback {
  questionText: string;
  userAnswerSummary: string | null;
  feedback: string;
  sequenceNo: number;
}

/** 本番模擬面接のレポート。 */
export interface MockReportResponse {
  sessionId: string;
  score: number;
  answerTendencyAnalysis: string;
  feedbacks: MockQuestionFeedback[];
  createdAt: string | null;
}

/** ユーザープロフィール(履歴書のような、面接練習・本番の質問生成/深掘りで参考にする情報)。 */
export interface UserProfileResponse {
  resumeText: string;
  updatedAt: string | null;
}
