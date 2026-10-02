import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router";
import { useSignOut } from "../../api/account";
import { messages } from "../../messages";
import { Button } from "../Button/Button";

/** Signed-in frame. Temporary: E06 replaces it with AppShell. */
export function PageFrame({ children }: { children: ReactNode }) {
  const signOut = useSignOut();
  const navigate = useNavigate();
  const frame = messages.frame;
  return (
    <div className="page">
      <header className="navbar navbar-expand-md d-print-none">
        <div className="container-xl">
          <Link to="/inbox" className="navbar-brand">
            {messages.appName}
          </Link>
          <div className="d-flex gap-2">
            <Button
              className="btn-ghost-secondary"
              icon="user"
              aria-label={frame.profile}
              onClick={() => void navigate("/profile")}
            >
              <span className="d-none d-md-inline">{frame.profile}</span>
            </Button>
            <Button
              className="btn-ghost-secondary"
              icon="logout"
              aria-label={frame.signOut}
              busy={signOut.isPending}
              onClick={() => signOut.mutate()}
            >
              <span className="d-none d-md-inline">{frame.signOut}</span>
            </Button>
          </div>
        </div>
      </header>
      <div className="page-wrapper">
        <main className="page-body">
          <div className="container-xl">{children}</div>
        </main>
      </div>
    </div>
  );
}
