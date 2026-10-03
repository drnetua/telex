import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { Link, useLocation } from "react-router";
import { useSignOut } from "../../api/account";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { ThemeSwitch } from "../../components/ThemeSwitch/ThemeSwitch";
import { messages } from "../../messages";
import { InboxAnnouncement, InboxPill } from "../InboxCounter";
import { usePulse } from "../pulse";
import { currentSection, sections, type Section } from "../sections";

const m = messages.shell;
const PHONE_QUERY = "(max-width: 767.98px)";

function subscribe(onChange: () => void) {
  if (typeof matchMedia !== "function") return () => undefined;
  const query = matchMedia(PHONE_QUERY);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

const isPhoneNow = () => typeof matchMedia === "function" && matchMedia(PHONE_QUERY).matches;

/** True below the 768 px tablet breakpoint. */
function usePhone() {
  return useSyncExternalStore(subscribe, isPhoneNow, () => false);
}

interface AppShellProps {
  email: string;
  /** Slot at the top of the main column (T13). */
  banner?: ReactNode;
  children: ReactNode;
}

function SectionLabel({
  section,
  current,
  count,
}: {
  section: Section;
  current: boolean;
  count?: number;
}) {
  return (
    <>
      <Icon name={section.icon} size={20} />
      <span className={current ? "fw-bold" : undefined}>{m.sections[section.id]}</span>
      {section.id === "inbox" ? <InboxPill count={count} /> : null}
    </>
  );
}

function SignOutButton({ className }: { className?: string }) {
  const signOut = useSignOut();
  return (
    <Button
      className={className ?? "btn-ghost-dark"}
      icon="logout"
      busy={signOut.isPending}
      onClick={() => signOut.mutate()}
    >
      {signOut.isPending ? m.signingOut : m.signOut}
    </Button>
  );
}

function SideMenu({
  email,
  inboxCount,
  active,
}: {
  email: string;
  inboxCount?: number;
  active?: Section;
}) {
  return (
    <aside className="telex-sidemenu d-flex flex-column border-end">
      <div className="px-3 py-3 h3 mb-0">{messages.appName}</div>
      <nav aria-label={m.mainNav} className="flex-grow-1">
        <ul className="nav nav-pills flex-column">
          {sections.map((s) => {
            const current = active?.id === s.id;
            return (
              <li key={s.id} className="nav-item">
                <Link
                  to={s.path}
                  className={`nav-link d-flex align-items-center gap-2 telex-navitem${current ? " active" : ""}`}
                  aria-current={current ? "page" : undefined}
                >
                  <SectionLabel section={s} current={current} count={inboxCount} />
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="p-3 border-top d-flex flex-column gap-2">
        <span className="text-secondary text-break">{email}</span>
        <ThemeSwitch variant="menu" />
        <SignOutButton />
      </div>
    </aside>
  );
}

function MoreSheet({ active, onClose }: { active?: Section; onClose: () => void }) {
  const sheet = useRef<HTMLDivElement>(null);
  useEffect(() => {
    sheet.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <>
      <div className="offcanvas-backdrop fade show" onClick={onClose} />
      <div
        ref={sheet}
        className="offcanvas offcanvas-bottom show h-auto"
        role="dialog"
        aria-modal="true"
        aria-label={m.more}
        tabIndex={-1}
      >
        <div className="offcanvas-header">
          <h2 className="offcanvas-title h4">{m.more}</h2>
          <Button
            className="btn-ghost-dark touch-target"
            icon="x"
            aria-label={m.close}
            onClick={onClose}
          />
        </div>
        <div className="offcanvas-body d-flex flex-column gap-3">
          <ul className="list-group">
            {sections
              .filter((s) => s.phone === "more")
              .map((s) => {
                const current = active?.id === s.id;
                return (
                  <li key={s.id} className="list-group-item p-0">
                    <Link
                      to={s.path}
                      className={`d-flex align-items-center gap-2 px-3 telex-moreitem${current ? " active" : ""}`}
                      aria-current={current ? "page" : undefined}
                    >
                      <SectionLabel section={s} current={current} />
                    </Link>
                  </li>
                );
              })}
          </ul>
          <hr className="my-0" />
          <div className="d-flex align-items-center justify-content-between gap-2 flex-wrap">
            <span>{messages.theme.label}</span>
            <ThemeSwitch variant="segmented" />
          </div>
          <SignOutButton className="btn-outline-secondary" />
        </div>
      </div>
    </>
  );
}

function PhoneBar({
  inboxCount,
  active,
  moreOpen,
  onMore,
  moreButton,
}: {
  inboxCount?: number;
  active?: Section;
  moreOpen: boolean;
  onMore: () => void;
  moreButton: React.RefObject<HTMLButtonElement | null>;
}) {
  const moreCurrent = active?.phone === "more";
  return (
    <nav aria-label={m.mainNav} className="telex-bottombar d-flex border-top fixed-bottom">
      {sections
        .filter((s) => s.phone === "bar")
        .map((s) => {
          const current = active?.id === s.id;
          return (
            <Link
              key={s.id}
              to={s.path}
              className={`telex-baritem${current ? " active" : ""}`}
              aria-current={current ? "page" : undefined}
            >
              <SectionLabel section={s} current={current} count={inboxCount} />
            </Link>
          );
        })}
      <button
        ref={moreButton}
        type="button"
        className={`telex-baritem${moreCurrent ? " active" : ""}`}
        aria-haspopup="dialog"
        aria-expanded={moreOpen}
        aria-current={moreCurrent ? "page" : undefined}
        onClick={onMore}
      >
        <Icon name="dots" size={20} />
        <span className={moreCurrent ? "fw-bold" : undefined}>{m.more}</span>
      </button>
    </nav>
  );
}

/** Signed-in frame (C-01): side menu at 768 px and up, header + bottom bar + More sheet below it. */
export function AppShell({ email, banner, children }: AppShellProps) {
  // The one pulse consumer: undefined until the first answer; the last known count stays through failures.
  const inboxCount = usePulse().data?.inboxCount;
  const phone = usePhone();
  const location = useLocation();
  const active = currentSection(location.pathname);
  // The sheet is open for the path it was opened on, so navigating closes it without an effect.
  const [openAt, setOpenAt] = useState<string | null>(null);
  const moreButton = useRef<HTMLButtonElement>(null);
  const open = openAt === location.pathname && phone;

  const close = () => {
    setOpenAt(null);
    moreButton.current?.focus();
  };

  return (
    <div className="page flex-row">
      <InboxAnnouncement count={inboxCount} />
      {phone ? null : <SideMenu email={email} inboxCount={inboxCount} active={active} />}
      <div className="telex-main flex-grow-1 d-flex flex-column">
        {phone ? (
          <header className="navbar border-bottom px-3">
            <span className="h3 mb-0">{active ? m.sections[active.id] : messages.appName}</span>
          </header>
        ) : null}
        {banner}
        <main className="page-body flex-grow-1 mt-3">
          <div className="container-xl">{children}</div>
        </main>
      </div>
      {phone ? (
        <PhoneBar
          inboxCount={inboxCount}
          active={active}
          moreOpen={open}
          onMore={() => setOpenAt(location.pathname)}
          moreButton={moreButton}
        />
      ) : null}
      {open ? <MoreSheet active={active} onClose={close} /> : null}
    </div>
  );
}
