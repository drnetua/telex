import { useCallback, useState } from "react";
import { Navigate, useLocation, useParams, useSearchParams } from "react-router";
import { SYSTEM_KEYS, useModelCatalog, useModelProfiles } from "../../api/models";
import { Icon } from "../../components/Icon/Icon";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { CatalogTab } from "./CatalogTab";
import { NotFoundPage } from "../system/NotFoundPage";
import { ProfileEditorModal, type EditorNotice } from "./ProfileEditorModal";
import { ProfilesTab } from "./ProfilesTab";

const m = messages.models;

/** SCR-66. The tab lives in the URL: `?tab=catalog`, anything else is Profiles. */
export function ModelsPage() {
  const [params, setParams] = useSearchParams();
  const { id } = useParams();
  const location = useLocation();
  const url = location.pathname + location.search;
  const [goneAt, setGoneAt] = useState<string | null>(null);
  const onGone = useCallback(() => setGoneAt(url), [url]);
  const [dismissed, setDismissed] = useState<unknown>(null);
  const incoming = (location.state as { notice?: EditorNotice } | null)?.notice;
  const editing = id !== undefined || location.pathname.endsWith("/profiles/new");
  const tab = params.get("tab") === "catalog" ? "catalog" : "profiles";
  // Both lists are requested when the page opens; each tab renders from its own query.
  const profiles = useModelProfiles();
  useModelCatalog();

  function select(next: "profiles" | "catalog") {
    setParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        if (next === "catalog") p.set("tab", "catalog");
        else p.delete("tab");
        return p;
      },
      { replace: false },
    );
  }

  if (goneAt === url) return <NotFoundPage />;
  if (id !== undefined && SYSTEM_KEYS.includes(id))
    return <Navigate to="/settings/models" replace />;

  const tabs = [
    { id: "profiles", label: m.profilesTab },
    { id: "catalog", label: m.catalogTab },
  ] as const;

  return (
    <>
      <h1 className="page-title mb-3">{m.title}</h1>
      {tab === "profiles" && profiles.data?.aiConfigured === false ? (
        <div className="alert alert-warning d-flex align-items-center gap-2" role="alert">
          <Icon name="alert-triangle" size={20} />
          {m.notConfiguredAlert}
        </div>
      ) : null}
      <ul className="nav nav-tabs mb-3" role="tablist" aria-label={m.tabsLabel}>
        {tabs.map((t) => (
          <li className="nav-item" role="presentation" key={t.id}>
            <button
              type="button"
              role="tab"
              className={`nav-link${tab === t.id ? " active" : ""}`}
              aria-selected={tab === t.id}
              onClick={() => select(t.id)}
            >
              {t.label}
            </button>
          </li>
        ))}
      </ul>
      {tab === "catalog" ? (
        <CatalogTab onGoToProfiles={() => select("profiles")} />
      ) : (
        <ProfilesTab />
      )}
      {incoming && incoming !== dismissed ? (
        <Toast
          tone={incoming.tone}
          message={incoming.message}
          dismissLabel={m.dismiss}
          onDismiss={() => setDismissed(incoming)}
        />
      ) : null}
      {editing ? (
        <ProfileEditorModal
          key={url}
          profileId={id}
          from={params.get("from") ?? undefined}
          onGone={onGone}
        />
      ) : null}
    </>
  );
}
