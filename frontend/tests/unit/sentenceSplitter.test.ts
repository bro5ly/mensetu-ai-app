import { describe, expect, it } from "vitest";
import { extractSentences } from "@/lib/sentenceSplitter";

describe("extractSentences", () => {
  it("句点で終わる完成した文だけを取り出し残りは保持する", () => {
    const result = extractSentences("なるほど。そのとき何を意識");

    expect(result.sentences).toEqual(["なるほど。"]);
    expect(result.remainder).toBe("そのとき何を意識");
  });

  it("複数文が一度に確定した場合は全て返す", () => {
    const result = extractSentences("いいですね！次に進みましょう。続き");

    expect(result.sentences).toEqual(["いいですね！", "次に進みましょう。"]);
    expect(result.remainder).toBe("続き");
  });

  it("区切り文字が無ければ全体が残りになる", () => {
    const result = extractSentences("まだ途中の文章");

    expect(result.sentences).toEqual([]);
    expect(result.remainder).toBe("まだ途中の文章");
  });

  it("空文字列は何も返さない", () => {
    const result = extractSentences("");

    expect(result.sentences).toEqual([]);
    expect(result.remainder).toBe("");
  });
});
