interface LoadStateProps {
  state: "loading";
  rows?: number;
}

/** Skeleton placeholder shown while a screen loads. */
export function LoadState({ rows = 2 }: LoadStateProps) {
  return (
    <div role="status" aria-busy="true" aria-live="polite">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="placeholder-glow mb-3">
          <span className="placeholder col-12" />
        </div>
      ))}
    </div>
  );
}
