import { Outlet } from "react-router";
import { messages } from "../messages";

/** Bare layout for SCR-91/92/93: text wordmark, no session dependency. */
export function BareSystemLayout() {
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
    <div className="page">
      <header className="navbar navbar-expand-md d-print-none">
        <div className="container-xl">
          <span className="navbar-brand">{messages.appName}</span>
        </div>
      </header>
      <div className="page-wrapper">
        <main className="page-body">
          <div className="container-xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
