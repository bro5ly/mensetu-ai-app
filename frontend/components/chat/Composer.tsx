import { useRef, useState } from "react";

interface Props {
  disabled: boolean;
  onSend: (text: string) => void;
}

const MAX_TEXTAREA_HEIGHT_PX = 160;

export function Composer({ disabled, onSend }: Props) {
  const [value, setValue] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const resize = (el: HTMLTextAreaElement) => {
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, MAX_TEXTAREA_HEIGHT_PX)}px`;
  };

  const submit = () => {
    const text = value.trim();
    if (!text || disabled) return;
    onSend(text);
    setValue("");
    const el = textareaRef.current;
    if (el) {
      el.style.height = "auto";
    }
  };

  return (
    <form
      className="mt-1 flex w-full items-end gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          resize(e.target);
        }}
        onKeyDown={(e) => {
          // Enterで送信、Shift+Enter(または変換確定中のEnter)で改行。
          if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            submit();
          }
        }}
        disabled={disabled}
        rows={1}
        placeholder="テキストで入力することもできます(Shift+Enterで改行)"
        className="min-w-0 flex-1 resize-none rounded-[22px] border border-line px-4 py-[11px] text-sm leading-relaxed outline-none focus:border-accent disabled:bg-panel-sidebar"
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
