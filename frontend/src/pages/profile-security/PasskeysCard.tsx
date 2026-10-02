import { useEffect, useRef, useState } from "react";
import { ApiFailure } from "../../api/client";
import { passkeysKey, usePasskeys, useRemovePasskey, type Passkey } from "../../api/account";
import { canCreatePasskey, createPasskey, PasskeyCancelled } from "../../api/webauthn";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "../../components/Button/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { Icon } from "../../components/Icon/Icon";
import { LoadState } from "../../components/LoadState/LoadState";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { routeFailure } from "../auth/failure";
import { formatDate, formatWhen } from "./relativeTime";

const m = messages.profileSecurity;

export function PasskeysCard() {
  const client = useQueryClient();
  const passkeys = usePasskeys();
  const remove = useRemovePasskey();
  const [supported, setSupported] = useState(true);
  const [adding, setAdding] = useState(false);
  const [failed, setFailed] = useState(false);
  const [target, setTarget] = useState<Passkey | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const removed = useRef(false);

  useEffect(() => {
    let live = true;
    void canCreatePasskey().then((ok) => {
      if (live) setSupported(ok);
    });
    return () => {
      live = false;
    };
  }, []);

  function askRemove(p: Passkey) {
    removed.current = false;
    setTarget(p);
  }

  async function add() {
    setAdding(true);
    setFailed(false);
    try {
      await createPasskey();
      await client.invalidateQueries({ queryKey: passkeysKey });
    } catch (error) {
      if (error instanceof PasskeyCancelled) return;
      if (error instanceof ApiFailure && error.code === "passkey-registration-failed") {
        setFailed(true);
        return;
      }
      if (!routeFailure(error, add)) setFailed(true);
    } finally {
      setAdding(false);
    }
  }

  const addButton = supported ? (
    <Button className="btn-secondary" icon="plus" busy={adding} onClick={() => void add()}>
      {adding ? m.adding : m.add}
    </Button>
  ) : (
    <small className="text-secondary d-inline-flex align-items-center gap-1">
      <Icon name="info-circle" size={16} />
      {m.unsupported}
    </small>
  );

  const items = passkeys.data;
  return (
    <section className="card mb-4" aria-labelledby="passkeys-title">
      <div className="card-header d-flex justify-content-between align-items-center gap-2">
        <h2 id="passkeys-title" className="card-title" tabIndex={-1} ref={heading}>
          {m.passkeysTitle}
        </h2>
        {items && items.length > 0 ? addButton : null}
      </div>
      {!items ? (
        <div className="card-body">
          <LoadState state="loading" rows={2} />
        </div>
      ) : items.length === 0 ? (
        <div className="card-body">
          <EmptyState
            kind="first"
            icon="lock"
            title={m.emptyTitle}
            headingLevel={2}
            action={addButton}
          >
            {m.emptyBody}
          </EmptyState>
        </div>
      ) : (
        <ul className="list-group list-group-flush">
          {items.map((p) => (
            <li
              key={p.id}
              className="list-group-item d-flex flex-wrap justify-content-between align-items-center gap-2"
            >
              <div>
                <h4 className="mb-1">{p.label}</h4>
                <small className="text-secondary">
                  {p.lastUsedAt
                    ? `${m.created(formatDate(p.createdAt))} · ${m.lastUsed(formatWhen(p.lastUsedAt))}`
                    : `${m.created(formatDate(p.createdAt))} · ${m.neverUsed}`}
                </small>
              </div>
              <Button className="btn-ghost-secondary" icon="trash" onClick={() => askRemove(p)}>
                {m.remove}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {target ? (
        <ConfirmDialog
          title={m.removeTitle}
          confirmLabel={m.removeConfirm}
          cancelLabel={m.removeCancel}
          busy={remove.isPending}
          busyLabel={m.removing}
          returnFocusTo={() => (removed.current ? heading.current : null)}
          onCancel={() => setTarget(null)}
          onConfirm={() =>
            remove.mutate(target.id, {
              onSuccess: () => (removed.current = true),
              onSettled: () => setTarget(null),
            })
          }
        >
          {m.removeBody(target.label)}
        </ConfirmDialog>
      ) : null}
      {failed ? (
        <Toast
          tone="error"
          message={m.addFailed}
          dismissLabel={m.dismiss}
          onDismiss={() => setFailed(false)}
        />
      ) : null}
    </section>
  );
}
