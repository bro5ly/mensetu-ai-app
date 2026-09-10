import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Composer } from "@/components/chat/Composer";

describe("Composer", () => {
  it("Enterで送信し、入力欄が空になる", () => {
    const onSend = vi.fn();
    render(<Composer disabled={false} onSend={onSend} />);

    const textarea = screen.getByPlaceholderText("テキストで入力することもできます(Shift+Enterで改行)");
    fireEvent.change(textarea, { target: { value: "こんにちは" } });
    fireEvent.keyDown(textarea, { key: "Enter" });

    expect(onSend).toHaveBeenCalledWith("こんにちは");
    expect((textarea as HTMLTextAreaElement).value).toBe("");
  });

  it("Shift+Enterでは送信されず改行できる", () => {
    const onSend = vi.fn();
    render(<Composer disabled={false} onSend={onSend} />);

    const textarea = screen.getByPlaceholderText("テキストで入力することもできます(Shift+Enterで改行)");
    fireEvent.change(textarea, { target: { value: "1行目" } });
    fireEvent.keyDown(textarea, { key: "Enter", shiftKey: true });
    fireEvent.change(textarea, { target: { value: "1行目\n2行目" } });

    expect(onSend).not.toHaveBeenCalled();
    expect((textarea as HTMLTextAreaElement).value).toBe("1行目\n2行目");
  });

  it("空白のみの入力では送信ボタンが無効化される", () => {
    render(<Composer disabled={false} onSend={() => {}} />);

    const textarea = screen.getByPlaceholderText("テキストで入力することもできます(Shift+Enterで改行)");
    fireEvent.change(textarea, { target: { value: "   " } });

    const button = screen.getByRole("button", { name: "送信" }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });
});
