import { useCallback, useEffect, useRef } from "react";

export interface Recorder {
  start: () => Promise<void>;
  /**
   * 録音を止める。MediaRecorder.stop() は残りバッファ分の最終 dataavailable を
   * 非同期に発火してから stop イベントを発火する仕様のため、その最終チャンクの
   * onChunk 呼び出しが完了するまで待ってから resolve する（呼び出し側が停止直後に
   * end_turn を送っても、発話末尾の音声を送り切る前に届かないようにするため）。
   */
  stop: () => Promise<void>;
  isSupported: boolean;
}

/**
 * マイク録音を扱うフック。timeslice ごとに音声チャンクを {@code onChunk} に渡す。
 */
export function useRecorder(onChunk: (chunk: Blob) => void): Recorder {
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const onChunkRef = useRef(onChunk);
  onChunkRef.current = onChunk;

  const stop = useCallback((): Promise<void> => {
    const recorder = recorderRef.current;
    const stream = streamRef.current;
    recorderRef.current = null;
    streamRef.current = null;

    if (!recorder || recorder.state === "inactive") {
      stream?.getTracks().forEach((track) => track.stop());
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      recorder.addEventListener(
        "stop",
        () => {
          stream?.getTracks().forEach((track) => track.stop());
          resolve();
        },
        { once: true },
      );
      recorder.stop();
    });
  }, []);

  const start = useCallback(async () => {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    streamRef.current = stream;
    const recorder = new MediaRecorder(stream);
    recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) onChunkRef.current(event.data);
    };
    recorder.start(300);
    recorderRef.current = recorder;
  }, []);

  useEffect(() => {
    return () => {
      void stop();
    };
  }, [stop]);

  const isSupported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;

  return { start, stop, isSupported };
}
