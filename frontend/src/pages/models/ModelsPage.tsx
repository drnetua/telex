import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from "react";
import { Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router";
import { SYSTEM_KEYS, useModelCatalog, useModelProfiles } from "../../api/models";
import { AppFrame, BareSystemFrame } from "../../app/layouts";
import { Icon } from "../../components/Icon/Icon";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { CatalogTab } from "./CatalogTab";
import { NotFoundPage } from "../system/NotFoundPage";
import { NoticeContext, type EditorNotice } from "./notice";
import { ProfileEditorModal } from "./ProfileEditorModal";
import { ProfilesTab } from "./ProfilesTab";

const m = messages.models;
type TabId = "profiles" | "catalog";
const TABS: { id: TabId; label: string }[] = [
  { id: "profiles", label: m.profilesTab },
  { id: "catalog", label: m.catalogTab },
];

/** SCR-66. The tab lives in the URL: `?tab=catalog`, anything else is Profiles. */
export function ModelsPage() {
  const [params, setParams] = useSearchParams();
  const { id } = useParams();
  const location = useLocation();
  const url = location.pathname + location.search;
  const [goneAt, setGoneAt] = useState<string | null>(null);
  const onGone = useCallback(() => setGoneAt(url), [url]);
  const navigate = useNavigate();
  // A notice belongs to the URL entry it was raised for; the next navigation drops it.
  const [entry, setEntry] = useState<{ notice: EditorNotice; url: string } | null>(null);
  const incoming = (location.state as { notice?: EditorNotice } | null)?.notice;
  // A result notice is shown once: take it out of the history entry so reload and Back/Forward don't replay it.
  if (incoming && incoming !== entry?.notice) setEntry({ notice: incoming, url });
  useEffect(() => {
    if (incoming) void navigate(url, { replace: true, state: null });
  }, [incoming, url, navigate]);
  // Navigating on drops the notice for good, so returning to its URL doesn't bring it back.
  if (entry && entry.url !== url) setEntry(null);
  const notice = entry && entry.url === url ? entry.notice : null;
  const clear = useCallback(() => setEntry(null), []);
  const notices = useMemo(
    () => ({ notify: (n: EditorNotice) => setEntry({ notice: n, url }), clear }),
    [url, clear],
  );
  const editing = id !== undefined || location.pathname.endsWith("/profiles/new");
  const tab: TabId = params.get("tab") === "catalog" ? "catalog" : "profiles";
  const tabsId = useId();
  const tabRefs = useRef<Partial<Record<TabId, HTMLButtonElement | null>>>({});

  function select(next: TabId) {
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

  function onTabKey(e: ReactKeyboardEvent) {
    const at = TABS.findIndex((t) => t.id === tab);
    const to =
      e.key === "ArrowRight"
        ? (at + 1) % TABS.length
        : e.key === "ArrowLeft"
          ? (at + TABS.length - 1) % TABS.length
          : e.key === "Home"
            ? 0
            : e.key === "End"
              ? TABS.length - 1
              : -1;
    const next = TABS[to];
    if (!next) return;
    e.preventDefault();
    select(next.id);
    tabRefs.current[next.id]?.focus();
  }

  // SCR-91 is the bare system layout, outside the app frame, the same for a missing and a foreign id.
  if (goneAt === url)
    return (
      <BareSystemFrame>
        <NotFoundPage />
      </BareSystemFrame>
    );
  if (id !== undefined && SYSTEM_KEYS.includes(id))
    return <Navigate to="/settings/models" replace />;

  return (
    <AppFrame>
      <NoticeContext.Provider value={notices}>
        <h1 className="page-title mb-3">{m.title}</h1>
        <NotConfiguredAlert />
        <ul className="nav nav-tabs mb-3" role="tablist" aria-label={m.tabsLabel}>
          {TABS.map((t) => (
            <li className="nav-item" role="presentation" key={t.id}>
              <button
                type="button"
                role="tab"
                id={`${tabsId}-tab-${t.id}`}
                ref={(el) => {
                  tabRefs.current[t.id] = el;
                }}
                className={`nav-link${tab === t.id ? " active" : ""}`}
                aria-selected={tab === t.id}
                aria-controls={`${tabsId}-panel`}
                tabIndex={tab === t.id ? 0 : -1}
                onClick={() => select(t.id)}
                onKeyDown={onTabKey}
              >
                {t.label}
              </button>
            </li>
          ))}
        </ul>
        <div role="tabpanel" id={`${tabsId}-panel`} aria-labelledby={`${tabsId}-tab-${tab}`}>
          {tab === "catalog" ? (
            <CatalogTab onGoToProfiles={() => select("profiles")} />
          ) : (
            <ProfilesTab />
          )}
        </div>
        {notice ? (
          <Toast
            tone={notice.tone}
            message={notice.message}
            dismissLabel={m.dismiss}
            onDismiss={clear}
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
      </NoticeContext.Provider>
    </AppFrame>
  );
}

/** Above the tabs on both tabs. Mounted inside the frame, so both lists are requested once, after the shell loads. */
function NotConfiguredAlert() {
  const profiles = useModelProfiles();
  const catalog = useModelCatalog();
  if (profiles.data?.aiConfigured !== false && catalog.data?.state !== "not-configured")
    return null;
  return (
    <div className="alert alert-warning d-flex align-items-center gap-2" role="alert">
      <Icon name="alert-triangle" size={20} />
      {m.notConfiguredAlert}
    </div>
  );
}
