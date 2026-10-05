interface LoadStateProps {
  state: "loading";
  rows?: number;
  /** False once loading has failed and nothing is in flight, so assistive tech stops waiting. */
  busy?: boolean;
}

/** Skeleton placeholder shown while a screen loads. */
export function LoadState({ rows = 2, busy = true }: LoadStateProps) {
  return (
    <div role="status" aria-busy={busy} aria-live="polite">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="placeholder-glow mb-3">
          <span className="placeholder col-12" />
        </div>
      ))}
    </div>
  );
}
