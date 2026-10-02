import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { Link } from "react-router";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { messages } from "../../messages";

export function SessionEndedPage() {
  const queryClient = useQueryClient();
  useEffect(() => {
    queryClient.clear();
  }, [queryClient]);
  return (
    <EmptyState
      kind="blocked"
      icon="logout"
      title={messages.sessionEnded.title}
      action={
        <Link className="btn btn-primary" to="/sign-in">
          {messages.sessionEnded.action}
        </Link>
      }
    >
      {messages.sessionEnded.body}
    </EmptyState>
  );
}
