import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createAudioQueuePlayer } from "@/lib/audioQueuePlayer";

class FakeAudio {
  static instances: FakeAudio[] = [];
  src = "";
  onended: (() => void) | null = null;
  onerror: (() => void) | null = null;
  playCalls = 0;

  constructor() {
    FakeAudio.instances.push(this);
  }

  play() {
    this.playCalls += 1;
    return Promise.resolve();
  }

  pause() {}

  finish() {
    this.onended?.();
  }

  fail() {
    this.onerror?.();
  }
}

beforeEach(() => {
  FakeAudio.instances = [];
  vi.stubGlobal("URL", {
    createObjectURL: vi.fn(() => `blob:fake-${FakeAudio.instances.length}`),
    revokeObjectURL: vi.fn(),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function blob() {
  return new Blob([new Uint8Array([1, 2, 3])], { type: "audio/wav" });
}

describe("createAudioQueuePlayer", () => {
  it("1件目を即座に再生する", () => {
    const player = createAudioQueuePlayer(() => new FakeAudio());
    player.enqueue(blob());

    expect(FakeAudio.instances).toHaveLength(1);
    expect(FakeAudio.instances[0].playCalls).toBe(1);
  });

  it("再生中に届いた分は再生が終わるまでキューに積んでおく", () => {
    const player = createAudioQueuePlayer(() => new FakeAudio());
    player.enqueue(blob());
    player.enqueue(blob());

    // まだ1件目の再生中なので2件目のAudioは生成されない
    expect(FakeAudio.instances).toHaveLength(1);

    FakeAudio.instances[0].finish();

    expect(FakeAudio.instances).toHaveLength(2);
    expect(FakeAudio.instances[1].playCalls).toBe(1);
  });

  it("再生エラーでも次のキューへ進む", () => {
    const player = createAudioQueuePlayer(() => new FakeAudio());
    player.enqueue(blob());
    player.enqueue(blob());

    FakeAudio.instances[0].fail();

    expect(FakeAudio.instances).toHaveLength(2);
  });

  it("stop() でキューを破棄し再生中の音声も止める", () => {
    const player = createAudioQueuePlayer(() => new FakeAudio());
    player.enqueue(blob());
    const first = FakeAudio.instances[0];
    const pauseSpy = vi.spyOn(first, "pause");

    player.enqueue(blob());
    player.stop();
    first.finish();

    // stop() 時点でキューは空になっているので、1件目の再生終了後も新しいAudioは作られない
    expect(pauseSpy).toHaveBeenCalledTimes(1);
    expect(FakeAudio.instances).toHaveLength(1);
  });

  it("再生開始・全件終了・stop()で再生状態の変化を通知する", () => {
    const onPlayingChange = vi.fn();
    const player = createAudioQueuePlayer(() => new FakeAudio(), onPlayingChange);

    player.enqueue(blob());
    expect(onPlayingChange).toHaveBeenLastCalledWith(true);

    FakeAudio.instances[0].finish();
    expect(onPlayingChange).toHaveBeenLastCalledWith(false);

    player.enqueue(blob());
    expect(onPlayingChange).toHaveBeenLastCalledWith(true);
    player.stop();
    expect(onPlayingChange).toHaveBeenLastCalledWith(false);
  });

  it("onStartは再生が実際に始まったタイミングで呼ばれる", () => {
    const player = createAudioQueuePlayer(() => new FakeAudio());
    const onStart1 = vi.fn();
    const onStart2 = vi.fn();

    player.enqueue(blob(), onStart1);
    expect(onStart1).toHaveBeenCalledTimes(1);

    player.enqueue(blob(), onStart2);
    expect(onStart2).not.toHaveBeenCalled();

    FakeAudio.instances[0].finish();
    expect(onStart2).toHaveBeenCalledTimes(1);
  });

  it("stop()でまだ再生されていない分もonStartを呼んでテキストが隠れたままにならないようにする", () => {
    const player = createAudioQueuePlayer(() => new FakeAudio());
    const onStart1 = vi.fn();
    const onStart2 = vi.fn();

    player.enqueue(blob(), onStart1);
    player.enqueue(blob(), onStart2);
    expect(onStart2).not.toHaveBeenCalled();

    player.stop();

    expect(onStart2).toHaveBeenCalledTimes(1);
    expect(onStart1).toHaveBeenCalledTimes(1);
  });
});
