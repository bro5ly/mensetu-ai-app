import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Sidebar } from "@/components/chat/Sidebar";

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    listCompanies: vi.fn(),
    getCompany: vi.fn(),
    getGenericQuestions: vi.fn(),
    addQuestion: vi.fn(),
    listSources: vi.fn(),
    addSources: vi.fn(),
    deleteSource: vi.fn(),
    getUserProfile: vi.fn(),
    updateUserProfile: vi.fn(),
  },
}));

import { api } from "@/lib/api";

const listCompanies = vi.mocked(api.listCompanies);
const getCompany = vi.mocked(api.getCompany);
const getGenericQuestions = vi.mocked(api.getGenericQuestions);
const addQuestion = vi.mocked(api.addQuestion);
const listSources = vi.mocked(api.listSources);
const addSources = vi.mocked(api.addSources);
const deleteSource = vi.mocked(api.deleteSource);
const getUserProfile = vi.mocked(api.getUserProfile);

const company = {
  id: "c1",
  name: "ABC商事",
  overview: null,
  createdAt: "",
  updatedAt: "",
  questions: [],
};

const genericCompany = {
  id: "generic",
  name: "汎用的な質問",
  overview: null,
  createdAt: "",
  updatedAt: "",
  questions: [],
};

beforeEach(() => {
  listCompanies.mockReset();
  getCompany.mockReset();
  getGenericQuestions.mockReset();
  addQuestion.mockReset();
  listSources.mockReset();
  addSources.mockReset();
  deleteSource.mockReset();
  getUserProfile.mockReset();
  listCompanies.mockResolvedValue([{ id: "c1", name: "ABC商事", createdAt: "" }]);
  getCompany.mockResolvedValue(company);
  getGenericQuestions.mockResolvedValue(genericCompany);
  getUserProfile.mockResolvedValue({ resumeText: "", updatedAt: null });
});

// getByPlaceholderText は既定で空白(改行含む)を単一スペースに正規化して比較する
const placeholder = "https://example.com/a https://example.com/b (1行に1URL、まとめて貼り付け可)";

describe("Sidebar の会社一覧", () => {
  it("会社が無ければ空状態のメッセージを表示する", async () => {
    listCompanies.mockResolvedValue([]);

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    await screen.findByText("まだ会社がありません。上の「会社を追加」から登録してください。");
  });
});

describe("Sidebar の汎用的な質問", () => {
  it("会社の上に汎用的な質問の質問一覧を表示し、選択するとonSelectQuestionを呼ぶ", async () => {
    getGenericQuestions.mockResolvedValue({
      ...genericCompany,
      questions: [
        {
          id: "gq1",
          companyId: "generic",
          questionText: "自己PRをしてください",
          internalCategory: "SELF_PR",
          displayOrder: 0,
          createdAt: "",
        },
      ],
    });
    const onSelectQuestion = vi.fn();

    render(<Sidebar activeQuestionId={null} onSelectQuestion={onSelectQuestion} />);

    await screen.findByText("汎用的な質問");
    fireEvent.click(await screen.findByText("自己PRをしてください"));

    expect(onSelectQuestion).toHaveBeenCalledWith(
      expect.objectContaining({ id: "gq1" }),
      "汎用的な質問",
    );
  });

  it("汎用的な質問の+から質問を追加できる", async () => {
    const created = {
      id: "gq2",
      companyId: "generic",
      questionText: "新しい汎用質問",
      internalCategory: null,
      displayOrder: 0,
      createdAt: "",
    };
    addQuestion.mockResolvedValue(created);

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    await screen.findByText("汎用的な質問");
    const addButtons = screen.getAllByTitle("質問を追加");
    fireEvent.click(addButtons[0]);

    const input = await screen.findByPlaceholderText("例：あなたの長所と短所を教えてください");
    fireEvent.change(input, { target: { value: "新しい汎用質問" } });
    fireEvent.click(screen.getByRole("button", { name: "追加する" }));

    await waitFor(() => expect(addQuestion).toHaveBeenCalledWith("generic", "新しい汎用質問"));
    expect(await screen.findByText("新しい汎用質問")).toBeTruthy();
  });

  it("見出しをクリックすると会社と同じように開閉できる", async () => {
    getGenericQuestions.mockResolvedValue({
      ...genericCompany,
      questions: [
        {
          id: "gq1",
          companyId: "generic",
          questionText: "自己PRをしてください",
          internalCategory: "SELF_PR",
          displayOrder: 0,
          createdAt: "",
        },
      ],
    });

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    // 既定は展開状態
    expect(await screen.findByText("自己PRをしてください")).toBeTruthy();

    // 見出しをクリックすると畳まれて質問が消える
    fireEvent.click(screen.getByText("汎用的な質問"));
    await waitFor(() =>
      expect(screen.queryByText("自己PRをしてください")).toBeNull(),
    );

    // もう一度クリックすると再び開く
    fireEvent.click(screen.getByText("汎用的な質問"));
    expect(await screen.findByText("自己PRをしてください")).toBeTruthy();
  });
});

describe("Sidebar のユーザー設定", () => {
  it("設定を押すとユーザー設定モーダルが開きプロフィールを取得する", async () => {
    getUserProfile.mockResolvedValue({ resumeText: "〇〇大学。強みは継続力。", updatedAt: null });

    render(
      <Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />,
    );

    fireEvent.click(await screen.findByTitle("設定"));
    expect(await screen.findByText("ユーザー設定")).toBeTruthy();
    expect(await screen.findByDisplayValue("〇〇大学。強みは継続力。")).toBeTruthy();
    expect(getUserProfile).toHaveBeenCalled();
  });
});

describe("Sidebar のソース管理", () => {
  it("複数のURLをまとめて貼り付けて一括追加できる", async () => {
    listSources.mockResolvedValue([]);
    addSources.mockResolvedValue({
      added: [
        {
          id: "s1",
          companyId: "c1",
          url: "https://example.com/a",
          title: "採用ページ",
          content: "本文A",
          fetchedAt: "",
          createdAt: "",
        },
        {
          id: "s2",
          companyId: "c1",
          url: "https://example.com/b",
          title: "口コミ",
          content: "本文B",
          fetchedAt: "",
          createdAt: "",
        },
      ],
      failed: [],
    });

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    fireEvent.click(await screen.findByTitle("ソースを管理"));
    await screen.findByText("ソースを管理");

    const textarea = screen.getByPlaceholderText(placeholder);
    fireEvent.change(textarea, {
      target: { value: "https://example.com/a\nhttps://example.com/b" },
    });
    fireEvent.click(screen.getByRole("button", { name: "追加" }));

    await screen.findByText("採用ページ");
    expect(addSources).toHaveBeenCalledWith("c1", [
      "https://example.com/a",
      "https://example.com/b",
    ]);
    expect(screen.getByText("口コミ")).toBeTruthy();
  });

  it("削除ボタンで一覧から取り除ける", async () => {
    listSources.mockResolvedValue([
      {
        id: "s1",
        companyId: "c1",
        url: "https://example.com/a",
        title: "採用ページ",
        content: "本文A",
        fetchedAt: "",
        createdAt: "",
      },
    ]);
    deleteSource.mockResolvedValue(undefined);

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    fireEvent.click(await screen.findByTitle("ソースを管理"));
    await screen.findByText("採用ページ");

    fireEvent.click(screen.getByTitle("削除"));

    await waitFor(() => expect(deleteSource).toHaveBeenCalledWith("s1"));
    await waitFor(() =>
      expect(screen.queryByText("採用ページ")).toBeNull(),
    );
  });

  it("一部のURLが失敗しても成功分は追加しエラーを表示する", async () => {
    listSources.mockResolvedValue([]);
    addSources.mockResolvedValue({
      added: [
        {
          id: "s1",
          companyId: "c1",
          url: "https://example.com/a",
          title: "採用ページ",
          content: "本文A",
          fetchedAt: "",
          createdAt: "",
        },
      ],
      failed: [{ url: "https://broken.example.com", message: "取得に失敗しました" }],
    });

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    fireEvent.click(await screen.findByTitle("ソースを管理"));
    const textarea = await screen.findByPlaceholderText(placeholder);

    fireEvent.change(textarea, {
      target: { value: "https://example.com/a\nhttps://broken.example.com" },
    });
    fireEvent.click(screen.getByRole("button", { name: "追加" }));

    await screen.findByText("採用ページ");
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("https://broken.example.com");
  });

  it("リクエスト自体が失敗した場合もエラーを表示する", async () => {
    listSources.mockResolvedValue([]);
    addSources.mockRejectedValue(new Error("ソースを追加できませんでした"));

    render(<Sidebar activeQuestionId={null} onSelectQuestion={() => {}} />);

    fireEvent.click(await screen.findByTitle("ソースを管理"));
    const textarea = await screen.findByPlaceholderText(placeholder);

    fireEvent.change(textarea, { target: { value: "https://example.com/broken" } });
    fireEvent.click(screen.getByRole("button", { name: "追加" }));

    await screen.findByRole("alert");
  });
});
