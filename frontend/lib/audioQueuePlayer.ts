/**
 * TTS音声Blobを受信順に再生するキュー。1ターンにつき1つのBlobが届く想定だが、
 * 再生中に次のBlobが届いた場合も取りこぼさず順番に再生する。
 */
export interface AudioQueuePlayer {
  enqueue: (blob: Blob) => void;
  stop: () => void;
}

interface AudioLike {
  src: string;
  onended: ((ev: Event) => void) | null;
  onerror: ((ev: Event) => void) | null;
  play: () => Promise<void> | void;
  pause: () => void;
}

export function createAudioQueuePlayer(
  createAudio: () => AudioLike = () => new Audio(),
): AudioQueuePlayer {
  const queue: Blob[] = [];
  let current: AudioLike | null = null;
  let currentUrl: string | null = null;

  function releaseCurrent(): void {
    if (currentUrl) {
      URL.revokeObjectURL(currentUrl);
      currentUrl = null;
    }
    current = null;
  }

  function playNext(): void {
    if (current || queue.length === 0) return;
    const blob = queue.shift()!;
    const typed = blob.type ? blob : new Blob([blob], { type: "audio/wav" });
    const url = URL.createObjectURL(typed);
    const audio = createAudio();
    current = audio;
    currentUrl = url;
    audio.onended = () => {
      releaseCurrent();
      playNext();
    };
    audio.onerror = () => {
      releaseCurrent();
      playNext();
    };
    audio.src = url;
    void audio.play()?.catch(() => {
      releaseCurrent();
      playNext();
    });
  }

  return {
    enqueue(blob: Blob) {
      queue.push(blob);
      playNext();
    },
    stop() {
      queue.length = 0;
      current?.pause();
      releaseCurrent();
    },
  };
}
