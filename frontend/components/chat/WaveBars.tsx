/** ui-design の録音中ウェーブ表示（24 本、決め打ちの高さ・周期）。 */
const BARS = Array.from({ length: 24 }, (_, i) => ({
  height: 8 + ((i * 7) % 26),
  duration: (0.6 + (i % 5) * 0.09).toFixed(2),
  delay: ((i % 7) * 0.06).toFixed(2),
}));

export function WaveBars() {
  return (
    <div className="flex h-[34px] items-center justify-center gap-[3px]">
      {BARS.map((bar, i) => (
        <span
          key={i}
          className="w-[3px] origin-center rounded-[2px] bg-accent"
          style={{
            height: bar.height,
            animation: `waveBar ${bar.duration}s ease-in-out infinite`,
            animationDelay: `${bar.delay}s`,
          }}
        />
      ))}
    </div>
  );
}
