import { Link } from "react-router";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { settingsEntries } from "./settingsRegistry";

/** SCR-69: the list of settings subsections. */
export function SettingsPage() {
  return (
    <>
      <h1 className="page-title mb-4">{messages.shell.sections.settings}</h1>
      <div className="list-group">
        {settingsEntries.map((e) => (
          <Link
            key={e.id}
            to={e.path}
            className="list-group-item list-group-item-action d-flex align-items-center gap-3"
          >
            <Icon name={e.icon} size={24} />
            <span className="flex-grow-1">
              <span className="h4 d-block mb-0">{e.title}</span>
              <small className="text-secondary">{e.hint}</small>
            </span>
            <Icon name="chevron-right" size={20} />
          </Link>
        ))}
      </div>
    </>
  );
}
