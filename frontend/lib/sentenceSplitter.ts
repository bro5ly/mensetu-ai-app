/**
 * ストリーミング中のテキストから、区切りが確定した文を句読点で逐次取り出す純粋関数。
 * バックエンドの SentenceSplitter(chat/SentenceSplitter.java)と同じ規則。
 * AIの発話テキストを、対応する文の音声再生開始に合わせて表示するために使う。
 */
const BOUNDARY_CHARS = "。！？!?";

export interface SentenceSplitResult {
  sentences: string[];
  remainder: string;
}

export function extractSentences(buffer: string): SentenceSplitResult {
  if (!buffer) {
    return { sentences: [], remainder: "" };
  }
  const sentences: string[] = [];
  let start = 0;
  for (let i = 0; i < buffer.length; i++) {
    if (BOUNDARY_CHARS.includes(buffer[i])) {
      sentences.push(buffer.slice(start, i + 1));
      start = i + 1;
    }
  }
  return { sentences, remainder: buffer.slice(start) };
}
