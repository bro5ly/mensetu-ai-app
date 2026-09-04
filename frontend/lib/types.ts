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

/** チャット表示用のメッセージ（ストリーミング中の未確定分も含む）。 */
export interface ChatMessage {
  id: string;
  role: MessageRole;
  messageType: MessageType;
  content: string;
  streaming: boolean;
}
