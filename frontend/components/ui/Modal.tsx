import { useEffect } from "react";

interface Props {
  onClose: () => void;
  children: React.ReactNode;
  /** カード幅。ui-design のモーダルは用途ごとに 300 / 340 / 520px */
  maxWidth?: number;
  labelledBy?: string;
}

/**
 * ui-design の共通モーダル。半透明のオーバーレイ＋角丸カード。
 * オーバーレイクリックと Esc で閉じる。
 */
export function Modal({ onClose, children, maxWidth = 340, labelledBy }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[oklch(0.2_0.006_60/0.5)] p-6"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        className="max-h-[84vh] w-full overflow-y-auto rounded-[20px] bg-white p-[26px] shadow-[0_20px_50px_-15px_oklch(0.2_0.01_60/0.35)]"
        style={{ maxWidth }}
      >
        {children}
      </div>
    </div>
  );
}
