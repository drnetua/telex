import { Navigate, Route, Routes } from "react-router";
import { messages } from "../messages";
import { CheckEmailPage } from "../pages/check-email/CheckEmailPage";
import { CreatePasskeyPage } from "../pages/create-passkey/CreatePasskeyPage";
import { ConfirmLinkPage } from "../pages/confirm-link/ConfirmLinkPage";
import { SignInPage } from "../pages/sign-in/SignInPage";
import { NotFoundPage } from "../pages/system/NotFoundPage";
import { SessionEndedPage } from "../pages/system/SessionEndedPage";
import { AppLayout, AuthLayout, BareSystemLayout } from "./layouts";

function Placeholder({ title, body }: { title: string; body?: string }) {
  return (
    <div className="empty">
      <h1 className="empty-title">{title}</h1>
      {body ? <p className="empty-subtitle text-secondary">{body}</p> : null}
    </div>
  );
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/sign-in" element={<SignInPage />} />
        <Route path="/sign-in/check-email" element={<CheckEmailPage />} />
        <Route path="/sign-in/link" element={<ConfirmLinkPage />} />
        <Route path="/welcome/passkey" element={<CreatePasskeyPage />} />
      </Route>
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to="/inbox" replace />} />
        <Route
          path="/inbox"
          element={<Placeholder title={messages.home.title} body={messages.home.body} />}
        />
        <Route path="/profile" element={<Placeholder title="Profile and security" />} />
      </Route>
      <Route element={<BareSystemLayout />}>
        <Route path="/session-ended" element={<SessionEndedPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
