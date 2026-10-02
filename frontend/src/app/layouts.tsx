import type { ReactNode } from "react";
import { Outlet } from "react-router";
import { PageFrame } from "../components/PageFrame/PageFrame";
import { messages } from "../messages";

/** Bare frame for SCR-91/92/93: text wordmark, no session dependency. */
export function BareSystemFrame({ children }: { children: ReactNode }) {
  return (
    <div className="page page-center">
      <div className="container-tight py-4">
        <div className="text-center mb-4 h2">{messages.appName}</div>
        <div className="card card-md">
          <div className="card-body">{children}</div>
        </div>
      </div>
    </div>
  );
}

export function BareSystemLayout() {
  return (
    <BareSystemFrame>
      <Outlet />
    </BareSystemFrame>
  );
}

export function AuthLayout() {
  return (
    <div className="page page-center">
      <div className="container-tight py-4">
        <div className="text-center mb-4 h2">{messages.appName}</div>
        <div className="card card-md">
          <div className="card-body">
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  );
}

export function AppLayout() {
  return (
    <PageFrame>
      <Outlet />
    </PageFrame>
  );
}
