import { Navigate, Route, Routes } from "react-router";
import { messages } from "../messages";
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
        <Route path="/sign-in" element={<Placeholder title="Sign in" />} />
        <Route path="/sign-in/check-email" element={<Placeholder title="Check your email" />} />
        <Route path="/sign-in/link" element={<Placeholder title="Signing you in" />} />
        <Route path="/welcome/passkey" element={<Placeholder title="Add a passkey" />} />
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
