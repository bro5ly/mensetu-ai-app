import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddCompanyWizard } from "@/components/chat/AddCompanyWizard";

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    researchCompany: vi.fn(),
    generateQuestions: vi.fn(),
    createCompany: vi.fn(),
  },
}));

import { api } from "@/lib/api";

const researchCompany = vi.mocked(api.researchCompany);
const generateQuestions = vi.mocked(api.generateQuestions);
const createCompany = vi.mocked(api.createCompany);

beforeEach(() => {
  researchCompany.mockReset();
  generateQuestions.mockReset();
  createCompany.mockReset();
});

describe("AddCompanyWizard", () => {
  it("会社名検索 → 決定 → 質問作成 → 追加まで進める", async () => {
    researchCompany.mockResolvedValue({ overview: "・挑戦的な社風\n面接では具体性が見られる。" });
    generateQuestions.mockResolvedValue({
      questions: ["志望動機を教えてください", "強みは何ですか", "逆質問はありますか"],
    });
    createCompany.mockResolvedValue({
      id: "c1",
      name: "ABC商事",
      overview: "・挑戦的な社風\n面接では具体性が見られる。",
      createdAt: "",
      updatedAt: "",
      questions: [
        {
          id: "q1",
          companyId: "c1",
          questionText: "志望動機を教えてください",
          internalCategory: null,
          displayOrder: 0,
          createdAt: "",
        },
      ],
    });

    const onCreated = vi.fn();
    render(<AddCompanyWizard onClose={() => {}} onCreated={onCreated} />);

    fireEvent.change(screen.getByPlaceholderText("例：ABCコーポレーション"), {
      target: { value: "ABC商事" },
    });
    fireEvent.click(screen.getByRole("button", { name: "検索する" }));

    expect(researchCompany).toHaveBeenCalledWith({ name: "ABC商事" });
    expect(await screen.findByText("ABC商事の特徴・面接傾向")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "決定" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "この内容から質問を作成する" }),
    );

    expect(generateQuestions).toHaveBeenCalledWith(
      "ABC商事",
      "・挑戦的な社風\n面接では具体性が見られる。",
    );
    expect(await screen.findByText("AIが作成した質問")).toBeTruthy();
    expect(screen.getByText("志望動機を教えてください")).toBeTruthy();

    fireEvent.click(
      screen.getByRole("button", { name: "この質問をリストに追加する" }),
    );

    // finalize は非同期。onCreated が呼ばれるまで待つ
    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
    expect(createCompany).toHaveBeenCalledWith({
      name: "ABC商事",
      overview: "・挑戦的な社風\n面接では具体性が見られる。",
      questions: ["志望動機を教えてください", "強みは何ですか", "逆質問はありますか"],
    });
  });

  it("検索が失敗するとエラーを表示する", async () => {
    researchCompany.mockRejectedValue(new Error("boom"));
    render(<AddCompanyWizard onClose={() => {}} onCreated={() => {}} />);

    fireEvent.change(screen.getByPlaceholderText("例：ABCコーポレーション"), {
      target: { value: "ABC商事" },
    });
    fireEvent.click(screen.getByRole("button", { name: "検索する" }));

    expect(await screen.findByText("企業情報を検索できませんでした")).toBeTruthy();
  });
});
