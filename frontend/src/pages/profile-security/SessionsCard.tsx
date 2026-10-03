import { useEndOtherSessions, useEndSession, useMe, useSessions } from "../../api/account";
import { Badge } from "../../components/Badge/Badge";
import { Button } from "../../components/Button/Button";
import { LoadState } from "../../components/LoadState/LoadState";
import { messages } from "../../messages";
import { formatWhen } from "./relativeTime";

const m = messages.profileSecurity;

export function SessionsCard() {
  const sessions = useSessions();
  const timeZone = useMe().data?.timeZone ?? null;
  const end = useEndSession();
  const endOthers = useEndOtherSessions();

  const items = sessions.data;
  const hasOthers = items?.some((s) => !s.current) ?? false;
  return (
    <section id="sessions" className="card mb-4" aria-labelledby="sessions-title">
      <div className="card-header">
        <h2 id="sessions-title" className="card-title">
          {m.sessionsTitle}
        </h2>
      </div>
      {!items ? (
        <div className="card-body">
          <LoadState state="loading" rows={2} />
        </div>
      ) : (
        <ul className="list-group list-group-flush">
          {items.map((s) => (
            <li
              key={s.id}
              className="list-group-item d-flex flex-wrap justify-content-between align-items-center gap-2"
            >
              <div>
                <div className="h4 mb-1">{s.userAgentLabel}</div>
                <small className="text-secondary">
                  {`${m.device[s.deviceType]} · ${m.active(formatWhen(s.lastActivityAt, timeZone))}`}
                </small>
              </div>
              {s.current ? (
                <Badge tone="neutral" icon="check">
                  {m.thisDevice}
                </Badge>
              ) : (
                <Button
                  className="btn-ghost-secondary"
                  busy={end.isPending && end.variables === s.id}
                  onClick={() => end.mutate(s.id)}
                >
                  {end.isPending && end.variables === s.id ? m.ending : m.endSession}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {items && hasOthers ? (
        <div className="card-footer">
          <Button
            className="btn-secondary"
            busy={endOthers.isPending}
            onClick={() => endOthers.mutate()}
          >
            {endOthers.isPending ? m.endingOthers : m.endOthers}
          </Button>
        </div>
      ) : null}
    </section>
  );
}
