import type { ReactNode } from "react";
import { Outlet } from "react-router";
import logo from "../assets/telex-logo-192.png";
import { useMe } from "../api/account";
import { LoadState } from "../components/LoadState/LoadState";
import { ThemeSaveToast } from "../components/ThemeSwitch/ThemeSwitch";
import { messages } from "../messages";
import { AppShell } from "../shell/AppShell/AppShell";
import { useAccountTheme } from "../shell/theme";
import { useSaveDetectedTimeZone } from "../shell/time";

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
        <div className="text-center mb-4">
          <img src={logo} alt={messages.appName} width={96} height={96} />
        </div>
        <div className="card card-md">
          <div className="card-body">
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  );
}

/** SCR-02: the auth card without the signed-in header. */
export function OnboardingLayout() {
  return (
    <div className="page page-center">
      <div className="container-tight py-4">
        <div className="text-center mb-4">
          <img src={logo} alt={messages.appName} width={96} height={96} />
        </div>
        <div className="card card-md">
          <div className="card-body">
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  );
}

/** Signed-in frame: the AppShell around a page. Pages that sometimes render bare (Models, SCR-91) use it directly. */
export function AppFrame({ children }: { children: ReactNode }) {
  const me = useMe();
  useAccountTheme(me.data);
  useSaveDetectedTimeZone(me.data);
  if (!me.data) return <LoadState state="loading" rows={3} />;
  return (
    <>
      <AppShell email={me.data.email}>
        {children}
      </AppShell>
      <ThemeSaveToast />
    </>
  );
}

export function AppLayout() {
  return (
    <AppFrame>
      <Outlet />
    </AppFrame>
  );
}
