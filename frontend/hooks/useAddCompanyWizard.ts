import { useCallback, useMemo, useReducer } from "react";
import { api, ApiError } from "@/lib/api";
import type { CompanyDetail, FetchedSourcePreview } from "@/lib/types";

export type WizardStep = "name" | "review" | "questions";
type Busy = "search" | "research" | "generate" | "create" | "fetchSource" | null;

interface State {
  step: WizardStep;
  name: string;
  overview: string;
  editing: boolean;
  confirmed: boolean;
  researchOpen: boolean;
  feedback: string;
  sourceUrl: string;
  sources: FetchedSourcePreview[];
  generated: string[] | null;
  busy: Busy;
  error: string | null;
}

const initialState: State = {
  step: "name",
  name: "",
  overview: "",
  editing: false,
  confirmed: false,
  researchOpen: false,
  feedback: "",
  sourceUrl: "",
  sources: [],
  generated: null,
  busy: null,
  error: null,
};

type Action =
  | { type: "reset" }
  | { type: "setName"; value: string }
  | { type: "setOverview"; value: string }
  | { type: "setFeedback"; value: string }
  | { type: "setSourceUrl"; value: string }
  | { type: "sourceAdded"; source: FetchedSourcePreview }
  | { type: "removeSource"; url: string }
  | { type: "toggleEdit" }
  | { type: "openResearch" }
  | { type: "cancelResearch" }
  | { type: "confirm" }
  | { type: "reopen" }
  | { type: "busy"; value: Busy }
  | { type: "error"; value: string | null }
  | { type: "researched"; overview: string }
  | { type: "generated"; questions: string[] };

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "reset":
      return initialState;
    case "setName":
      return { ...state, name: action.value, error: null };
    case "setOverview":
      return { ...state, overview: action.value };
    case "setFeedback":
      return { ...state, feedback: action.value };
    case "setSourceUrl":
      return { ...state, sourceUrl: action.value, error: null };
    case "sourceAdded":
      return {
        ...state,
        busy: null,
        sourceUrl: "",
        sources: [...state.sources, action.source],
      };
    case "removeSource":
      return { ...state, sources: state.sources.filter((s) => s.url !== action.url) };
    case "toggleEdit":
      return state.editing
        ? { ...state, editing: false }
        : { ...state, editing: true, confirmed: false };
    case "openResearch":
      return { ...state, researchOpen: true, feedback: "" };
    case "cancelResearch":
      return { ...state, researchOpen: false, feedback: "" };
    case "confirm":
      return { ...state, confirmed: true, editing: false };
    case "reopen":
      return { ...state, confirmed: false };
    case "busy":
      return { ...state, busy: action.value, error: null };
    case "error":
      return { ...state, busy: null, error: action.value };
    case "researched":
      return {
        ...state,
        busy: null,
        step: "review",
        overview: action.overview,
        editing: false,
        confirmed: false,
        researchOpen: false,
        feedback: "",
      };
    case "generated":
      return { ...state, busy: null, step: "questions", generated: action.questions };
    default:
      return state;
  }
}

function toMessage(e: unknown, fallback: string): string {
  return e instanceof ApiError ? e.message : fallback;
}

export interface AddCompanyWizard {
  state: State;
  stepIndex: number;
  canSearch: boolean;
  canAddSource: boolean;
  setName: (v: string) => void;
  setOverview: (v: string) => void;
  setFeedback: (v: string) => void;
  setSourceUrl: (v: string) => void;
  addSource: () => Promise<void>;
  removeSource: (url: string) => void;
  toggleEdit: () => void;
  openResearch: () => void;
  cancelResearch: () => void;
  confirm: () => void;
  reopen: () => void;
  reset: () => void;
  search: () => Promise<void>;
  reSearch: () => Promise<void>;
  generate: () => Promise<void>;
  finalize: () => Promise<CompanyDetail | null>;
}

export function useAddCompanyWizard(): AddCompanyWizard {
  const [state, dispatch] = useReducer(reducer, initialState);

  const addSource = useCallback(async () => {
    const url = state.sourceUrl.trim();
    if (!url || state.busy) return;
    dispatch({ type: "busy", value: "fetchSource" });
    try {
      const preview = await api.fetchSourcePreview(url);
      dispatch({ type: "sourceAdded", source: preview });
    } catch (e) {
      dispatch({ type: "error", value: toMessage(e, "URLの内容を取得できませんでした") });
    }
  }, [state.sourceUrl, state.busy]);

  const search = useCallback(async () => {
    const name = state.name.trim();
    if (!name || state.busy) return;
    dispatch({ type: "busy", value: "search" });
    try {
      const draft = await api.researchCompany({
        name,
        sources: state.sources.length > 0 ? state.sources : undefined,
      });
      dispatch({ type: "researched", overview: draft.overview });
    } catch (e) {
      dispatch({ type: "error", value: toMessage(e, "企業情報を検索できませんでした") });
    }
  }, [state.name, state.sources, state.busy]);

  const reSearch = useCallback(async () => {
    const name = state.name.trim();
    if (!name || state.busy) return;
    dispatch({ type: "busy", value: "research" });
    try {
      const draft = await api.researchCompany({
        name,
        sources: state.sources.length > 0 ? state.sources : undefined,
        currentOverview: state.overview,
        feedback: state.feedback.trim() || undefined,
      });
      dispatch({ type: "researched", overview: draft.overview });
    } catch (e) {
      dispatch({ type: "error", value: toMessage(e, "再検索できませんでした") });
    }
  }, [state.name, state.sources, state.overview, state.feedback, state.busy]);

  const generate = useCallback(async () => {
    const overview = state.overview.trim();
    if (!overview || state.busy) return;
    dispatch({ type: "busy", value: "generate" });
    try {
      const result = await api.generateQuestions(state.name.trim(), overview);
      dispatch({ type: "generated", questions: result.questions });
    } catch (e) {
      dispatch({ type: "error", value: toMessage(e, "質問を作成できませんでした") });
    }
  }, [state.name, state.overview, state.busy]);

  const finalize = useCallback(async (): Promise<CompanyDetail | null> => {
    if (!state.generated || state.busy) return null;
    dispatch({ type: "busy", value: "create" });
    try {
      const created = await api.createCompany({
        name: state.name.trim(),
        overview: state.overview.trim() || undefined,
        questions: state.generated,
        sources: state.sources.length > 0 ? state.sources : undefined,
      });
      return created;
    } catch (e) {
      dispatch({ type: "error", value: toMessage(e, "会社を追加できませんでした") });
      return null;
    }
  }, [state.name, state.overview, state.generated, state.sources, state.busy]);

  const stepIndex = useMemo(
    () => (state.step === "name" ? 0 : state.step === "review" ? 1 : 2),
    [state.step],
  );

  return {
    state,
    stepIndex,
    canSearch: state.name.trim().length > 0 && !state.busy,
    canAddSource: state.sourceUrl.trim().length > 0 && !state.busy,
    setName: (v) => dispatch({ type: "setName", value: v }),
    setOverview: (v) => dispatch({ type: "setOverview", value: v }),
    setFeedback: (v) => dispatch({ type: "setFeedback", value: v }),
    setSourceUrl: (v) => dispatch({ type: "setSourceUrl", value: v }),
    addSource,
    removeSource: (url) => dispatch({ type: "removeSource", url }),
    toggleEdit: () => dispatch({ type: "toggleEdit" }),
    openResearch: () => dispatch({ type: "openResearch" }),
    cancelResearch: () => dispatch({ type: "cancelResearch" }),
    confirm: () => dispatch({ type: "confirm" }),
    reopen: () => dispatch({ type: "reopen" }),
    reset: () => dispatch({ type: "reset" }),
    search,
    reSearch,
    generate,
    finalize,
  };
}
