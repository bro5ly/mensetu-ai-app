import { useEffect, useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { api, ApiError } from "@/lib/api";

interface Props {
  onClose: () => void;
}

/**
 * サイドバー左下の「設定」から開く、ユーザープロフィール(履歴書のような参考情報)の編集モーダル。
 * ここに登録した内容は、練習・本番モードのプロンプトに「候補者情報」として渡され、
 * 面接官/コーチが質問の話題を選ぶ際の参考になる(あくまで参考情報。実際の質問はこの会話で
 * 話した内容が中心になるようプロンプト側で指示している)。
 */
export function UserSettingsModal({ onClose }: Props) {
  const [resumeText, setResumeText] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const profile = await api.getUserProfile();
        if (!cancelled) setResumeText(profile.resumeText);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : "プロフィールを取得できませんでした");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const save = async () => {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const profile = await api.updateUserProfile(resumeText);
      setResumeText(profile.resumeText);
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "プロフィールを保存できませんでした");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal onClose={onClose} maxWidth={480} labelledBy="user-settings-title">
      <div id="user-settings-title" className="mb-1 text-[16px] font-bold text-ink">
        ユーザー設定
      </div>
      <p className="mb-3.5 text-[12.5px] leading-relaxed text-ink-faint">
        履歴書のような経歴・自己PR・志望動機などを登録しておくと、練習・本番の面接で
        質問の参考情報として使われます(実際の質問はこの会話で話した内容が中心です)。
      </p>

      {loading ? (
        <p className="py-6 text-center text-sm text-ink-faint">読み込み中...</p>
      ) : (
        <textarea
          autoFocus
          rows={10}
          value={resumeText}
          onChange={(e) => {
            setResumeText(e.target.value);
            setSaved(false);
          }}
          placeholder={
            "例:\n〇〇大学〇〇学部3年。\n強みは継続力で、3年間続けている〇〇の活動で発揮した。\n就活の軸は「人の成長を支える仕事」。"
          }
          disabled={saving}
          className="mb-3 w-full resize-y rounded-[10px] border border-line px-3.5 py-2.5 text-sm leading-relaxed outline-none focus:border-accent disabled:opacity-60"
        />
      )}

      {error && (
        <p className="mb-3 text-xs text-red-500" role="alert">
          {error}
        </p>
      )}
      {saved && !error && (
        <p className="mb-3 text-xs text-accent">保存しました</p>
      )}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={onClose}
          className="flex-1 rounded-xl border border-line bg-white px-3 py-2.5 text-sm font-semibold text-ink"
        >
          閉じる
        </button>
        <button
          type="button"
          onClick={() => void save()}
          disabled={loading || saving}
          className="flex-1 rounded-xl px-3.5 py-2.5 text-sm font-semibold text-white transition enabled:bg-accent disabled:cursor-not-allowed disabled:bg-panel-muted disabled:text-ink-faint"
        >
          {saving ? "保存中..." : "保存"}
        </button>
      </div>
    </Modal>
  );
}
