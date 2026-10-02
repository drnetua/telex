import { useEffect } from "react";
import { useLocation } from "react-router";
import { useMe } from "../../api/account";
import { messages } from "../../messages";
import { PasskeysCard } from "./PasskeysCard";
import { SessionsCard } from "./SessionsCard";

export function ProfileSecurityPage() {
  const me = useMe();
  const { hash } = useLocation();
  const m = messages.profileSecurity;

  useEffect(() => {
    if (hash === "#sessions") document.getElementById("sessions")?.scrollIntoView?.();
  }, [hash, me.data]);

  return (
    <>
      <h1 className="page-title">{m.title}</h1>
      {me.data ? <p className="text-secondary mb-4">{m.signedInAs(me.data.email)}</p> : null}
      <PasskeysCard />
      <SessionsCard />
    </>
  );
}
