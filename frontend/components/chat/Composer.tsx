import { useState } from "react";

interface Props {
  disabled: boolean;
  onSend: (text: string) => void;
}

export function Composer({ disabled, onSend }: Props) {
  const [value, setValue] = useState("");

  const submit = () => {
    const text = value.trim();
    if (!text || disabled) return;
    onSend(text);
    setValue("");
  };

  return (
    <form
      className="mt-1 flex w-full gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        disabled={disabled}
        placeholder="テキストで入力することもできます"
        className="min-w-0 flex-1 rounded-[22px] border border-line px-4 py-[11px] text-sm outline-none focus:border-accent disabled:bg-panel-sidebar"
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        className="rounded-[22px] px-[18px] py-[11px] text-sm font-semibold text-white transition enabled:bg-accent disabled:cursor-not-allowed disabled:bg-panel-muted disabled:text-ink-faint"
      >
        送信
      </button>
    </form>
  );
}
