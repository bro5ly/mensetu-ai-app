import { useCallback, useEffect, useRef } from "react";

export interface Recorder {
  start: () => Promise<void>;
  stop: () => void;
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

  const stop = useCallback(() => {
    recorderRef.current?.state !== "inactive" && recorderRef.current?.stop();
    streamRef.current?.getTracks().forEach((track) => track.stop());
    recorderRef.current = null;
    streamRef.current = null;
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

  useEffect(() => stop, [stop]);

  const isSupported =
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices &&
    typeof window !== "undefined" &&
    "MediaRecorder" in window;

  return { start, stop, isSupported };
}
