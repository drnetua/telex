import { Component, lazy, Suspense, useState, type ComponentType, type ReactNode } from "react";
import { EmptyState } from "../components/EmptyState/EmptyState";
import { Button } from "../components/Button/Button";
import { LoadState } from "../components/LoadState/LoadState";
import { messages } from "../messages";

class ChunkBoundary extends Component<
  { onRetry: () => void; children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    if (!this.state.failed) return this.props.children;
    const m = messages.shell.loadFailed;
    return (
      <EmptyState
        kind="blocked"
        icon="wifi-off"
        title={m.title}
        action={<Button onClick={this.props.onRetry}>{m.retry}</Button>}
      />
    );
  }
}

/** Lazy section page: `section-loading` while the chunk downloads, `section-load-failed` with Try again if it can't. */
export function SectionRoute({ load }: { load: () => Promise<{ default: ComponentType }> }) {
  const [page, setPage] = useState(() => ({ attempt: 0, Page: lazy(load) }));
  // A rejected React.lazy stays rejected, so each retry builds a fresh lazy component.
  const retry = () => setPage((p) => ({ attempt: p.attempt + 1, Page: lazy(load) }));
  const { Page } = page;
  return (
    <ChunkBoundary key={page.attempt} onRetry={retry}>
      <Suspense fallback={<LoadState state="loading" rows={3} />}>
        <Page />
      </Suspense>
    </ChunkBoundary>
  );
}
