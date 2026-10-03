import { Link } from "react-router";
import { Badge } from "../../components/Badge/Badge";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { messages } from "../../messages";
import { sections, type SectionId } from "../../shell/sections";

type UnbuiltId = keyof typeof messages.comingSoon.sentences;

/** SCR-94: the visible placeholder of a section whose epic has not built it yet. */
export function ComingSoonPage({ section }: { section: UnbuiltId }) {
  const m = messages.comingSoon;
  const icon = sections.find((s) => s.id === (section satisfies SectionId))!.icon;
  return (
    <>
      <h1 className="page-title mb-2">{messages.shell.sections[section]}</h1>
      <div className="mb-4">
        <Badge tone="neutral" icon="clock">
          {m.badge}
        </Badge>
      </div>
      <EmptyState
        kind="none"
        icon={icon}
        headingLevel={2}
        action={
          <Link to="/inbox" className="btn btn-primary">
            {m.goToInbox}
          </Link>
        }
      >
        {m.sentences[section]}
      </EmptyState>
    </>
  );
}
