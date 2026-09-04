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
      className="flex w-full max-w-2xl gap-2"
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
        className="min-w-0 flex-1 rounded-full border border-neutral-300 px-4 py-2.5 text-sm outline-none focus:border-violet-400 disabled:bg-neutral-50"
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        className="rounded-full bg-violet-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-violet-700 disabled:opacity-40"
      >
        送信
      </button>
    </form>
  );
}
