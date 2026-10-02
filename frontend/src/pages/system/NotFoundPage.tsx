import { Link } from "react-router";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { messages } from "../../messages";

export function NotFoundPage() {
  return (
    <EmptyState
      kind="none"
      icon="search"
      title={messages.notFound.title}
      action={
        <Link className="btn btn-primary" to="/inbox">
          {messages.notFound.action}
        </Link>
      }
    >
      {messages.notFound.body}
    </EmptyState>
  );
}
