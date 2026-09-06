import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Sidebar } from "@/components/chat/Sidebar";

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    listCompanies: vi.fn(),
    getCompany: vi.fn(),
    listSources: vi.fn(),
    addSources: vi.fn(),
    deleteSource: vi.fn(),
  },
}));

import { api } from "@/lib/api";

const listCompanies = vi.mocked(api.listCompanies);
const getCompany = vi.mocked(api.getCompany);
const listSources = vi.mocked(api.listSources);
const addSources = vi.mocked(api.addSources);
const deleteSource = vi.mocked(api.deleteSource);

const company = {
  id: "c1",
  name: "ABC商事",
  overview: null,
  createdAt: "",
  updatedAt: "",
  questions: [],
};

beforeEach(() => {
  listCompanies.mockReset();
  getCompany.mockReset();
  listSources.mockReset();
  addSources.mockReset();
  deleteSource.mockReset();
  listCompanies.mockResolvedValue([{ id: "c1", name: "ABC商事", createdAt: "" }]);
  getCompany.mockResolvedValue(company);
});

// getByPlaceholderText は既定で空白(改行含む)を単一スペースに正規化して比較する
const placeholder = "https://example.com/a https://example.com/b (1行に1URL、まとめて貼り付け可)";

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
