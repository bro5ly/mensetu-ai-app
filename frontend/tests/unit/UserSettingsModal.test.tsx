import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { UserSettingsModal } from "@/components/chat/UserSettingsModal";

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    getUserProfile: vi.fn(),
    updateUserProfile: vi.fn(),
  },
}));

import { api } from "@/lib/api";

const getUserProfile = vi.mocked(api.getUserProfile);
const updateUserProfile = vi.mocked(api.updateUserProfile);

beforeEach(() => {
  getUserProfile.mockReset();
  updateUserProfile.mockReset();
});

describe("UserSettingsModal", () => {
  it("開いたら登録済みのプロフィールを読み込んで表示する", async () => {
    getUserProfile.mockResolvedValue({ resumeText: "〇〇大学。強みは継続力。", updatedAt: null });

    render(<UserSettingsModal onClose={() => {}} />);

    expect(await screen.findByDisplayValue("〇〇大学。強みは継続力。")).toBeTruthy();
  });

  it("編集して保存するとupdateUserProfileが呼ばれ保存メッセージが出る", async () => {
    getUserProfile.mockResolvedValue({ resumeText: "", updatedAt: null });
    updateUserProfile.mockResolvedValue({ resumeText: "新しい経歴", updatedAt: null });

    render(<UserSettingsModal onClose={() => {}} />);

    const textarea = await screen.findByPlaceholderText(/例:/);
    fireEvent.change(textarea, { target: { value: "新しい経歴" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(updateUserProfile).toHaveBeenCalledWith("新しい経歴"));
    expect(await screen.findByText("保存しました")).toBeTruthy();
  });

  it("取得に失敗したらエラーを表示する", async () => {
    getUserProfile.mockRejectedValue(new Error("プロフィールを取得できませんでした"));

    render(<UserSettingsModal onClose={() => {}} />);

    expect(await screen.findByRole("alert")).toBeTruthy();
  });

  it("閉じるを押すとonCloseが呼ばれる", async () => {
    getUserProfile.mockResolvedValue({ resumeText: "", updatedAt: null });
    const onClose = vi.fn();

    render(<UserSettingsModal onClose={onClose} />);
    await screen.findByPlaceholderText(/例:/);

    fireEvent.click(screen.getByRole("button", { name: "閉じる" }));

    expect(onClose).toHaveBeenCalled();
  });
});
