import type { ComponentType } from "react";
import { Navigate, Route, Routes } from "react-router";
import { CheckEmailPage } from "../pages/check-email/CheckEmailPage";
import { CreatePasskeyPage } from "../pages/create-passkey/CreatePasskeyPage";
import { ConfirmLinkPage } from "../pages/confirm-link/ConfirmLinkPage";
import { messages } from "../messages";
import { ModelsPage } from "../pages/models/ModelsPage";
import { SignInPage } from "../pages/sign-in/SignInPage";
import { NotFoundPage } from "../pages/system/NotFoundPage";
import { SessionEndedPage } from "../pages/system/SessionEndedPage";
import { sections, type SectionId } from "../shell/sections";
import { AppLayout, AuthLayout, BareSystemLayout } from "./layouts";
import { SectionRoute } from "./SectionRoute";

type Loader = () => Promise<{ default: ComponentType }>;

const comingSoon =
  (id: keyof typeof messages.comingSoon.sentences): Loader =>
  () =>
    import("../pages/coming-soon/ComingSoonPage").then((m) => ({
      default: () => <m.ComingSoonPage section={id} />,
    }));

const built: Partial<Record<SectionId, Loader>> = {
  inbox: () => import("../pages/inbox/InboxPage").then((m) => ({ default: m.InboxPage })),
  settings: () =>
    import("../pages/settings/SettingsPage").then((m) => ({ default: m.SettingsPage })),
};

const profile: Loader = () =>
  import("../pages/profile-security/ProfileSecurityPage").then((m) => ({
    default: m.ProfileSecurityPage,
  }));

/** One stable loader per section, generated from the registry. */
const sectionLoaders = sections.map((s) => ({
  section: s,
  load:
    s.page === "page"
      ? built[s.id]!
      : comingSoon(s.id as keyof typeof messages.comingSoon.sentences),
}));

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
        {sectionLoaders.map(({ section, load }) => (
          <Route
            key={section.id}
            path={section.path}
            element={<SectionRoute key={section.id} load={load} />}
          />
        ))}
        <Route path="/profile" element={<SectionRoute key="profile" load={profile} />} />
      </Route>
      {/* ModelsPage frames itself so that SCR-91 can render bare, outside the app frame. */}
      <Route path="/settings/models" element={<ModelsPage />} />
      <Route path="/settings/models/profiles/new" element={<ModelsPage />} />
      <Route path="/settings/models/profiles/:id" element={<ModelsPage />} />
      <Route element={<BareSystemLayout />}>
        <Route path="/session-ended" element={<SessionEndedPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
