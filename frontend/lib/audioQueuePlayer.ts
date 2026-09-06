/**
 * TTS音声Blobを受信順に再生するキュー。1ターンにつき1つのBlobが届く想定だが、
 * 再生中に次のBlobが届いた場合も取りこぼさず順番に再生する。
 *
 * {@code enqueue} の第2引数 {@code onStart} は、そのBlobの再生が実際に始まったタイミングで
 * 呼ばれる(その文に対応するテキストを、音声に合わせて画面に表示するために使う)。
 * {@code stop()} でキューごと破棄された未再生分についても、テキストが永遠に表示されない
 * ままにならないよう {@code onStart} を呼んでおく。
 */
export interface AudioQueuePlayer {
  enqueue: (blob: Blob, onStart?: () => void) => void;
  stop: () => void;
}

interface AudioLike {
  src: string;
  onended: ((ev: Event) => void) | null;
  onerror: ((ev: Event) => void) | null;
  play: () => Promise<void> | void;
  pause: () => void;
}

interface QueueItem {
  blob: Blob;
  onStart?: () => void;
}

export function createAudioQueuePlayer(
  createAudio: () => AudioLike = () => new Audio(),
  onPlayingChange?: (playing: boolean) => void,
): AudioQueuePlayer {
  const queue: QueueItem[] = [];
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
    if (current) return;
    if (queue.length === 0) {
      onPlayingChange?.(false);
      return;
    }
    const item = queue.shift()!;
    const typed = item.blob.type ? item.blob : new Blob([item.blob], { type: "audio/wav" });
    const url = URL.createObjectURL(typed);
    const audio = createAudio();
    current = audio;
    currentUrl = url;
    onPlayingChange?.(true);
    item.onStart?.();
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
    enqueue(blob: Blob, onStart?: () => void) {
      queue.push({ blob, onStart });
      playNext();
    },
    stop() {
      const skipped = queue.splice(0, queue.length);
      current?.pause();
      releaseCurrent();
      onPlayingChange?.(false);
      skipped.forEach((item) => item.onStart?.());
    },
  };
}
