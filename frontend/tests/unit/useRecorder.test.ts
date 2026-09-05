import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useRecorder } from "@/hooks/useRecorder";

class FakeTrack {
  stopped = false;
  stop() {
    this.stopped = true;
  }
}

class FakeMediaRecorder {
  static instances: FakeMediaRecorder[] = [];
  state: "inactive" | "recording" = "recording";
  ondataavailable: ((e: { data: Blob }) => void) | null = null;
  private stopListeners: Array<() => void> = [];

  constructor(public stream: MediaStream) {
    FakeMediaRecorder.instances.push(this);
  }

  start(_timeslice?: number) {
    this.state = "recording";
  }

  addEventListener(type: string, cb: () => void) {
    if (type === "stop") this.stopListeners.push(cb);
  }

  // 実際の MediaRecorder と同じく、stop() は残りバッファの最終 dataavailable と
  // stop イベントを非同期(次のマイクロタスク)で発火する。
  stop() {
    this.state = "inactive";
    void Promise.resolve().then(() => {
      this.ondataavailable?.({ data: new Blob(["final-chunk"]) });
      this.stopListeners.forEach((cb) => cb());
    });
  }
}

beforeEach(() => {
  FakeMediaRecorder.instances = [];
  vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
  vi.stubGlobal("navigator", {
    mediaDevices: {
      getUserMedia: vi.fn(async () => {
        const tracks = [new FakeTrack()];
        return { getTracks: () => tracks };
      }),
    },
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useRecorder", () => {
  it("stop() は最終チャンクが onChunk に届いてから resolve する", async () => {
    const chunks: Blob[] = [];
    const { result } = renderHook(() => useRecorder((chunk) => chunks.push(chunk)));

    await act(async () => {
      await result.current.start();
    });

    await act(async () => {
      await result.current.stop();
    });

    expect(chunks).toHaveLength(1);
  });

  it("stop() でマイクのトラックを止める", async () => {
    const { result } = renderHook(() => useRecorder(() => {}));

    await act(async () => {
      await result.current.start();
    });
    const recorder = FakeMediaRecorder.instances[0];
    const track = recorder.stream.getTracks()[0] as unknown as FakeTrack;

    await act(async () => {
      await result.current.stop();
    });

    expect(track.stopped).toBe(true);
  });

  it("録音していない状態で stop() しても解決する", async () => {
    const { result } = renderHook(() => useRecorder(() => {}));

    await expect(result.current.stop()).resolves.toBeUndefined();
  });
});
