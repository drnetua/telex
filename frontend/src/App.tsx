import { QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { BrowserRouter } from "react-router";
import { useLiveUpdates } from "./api/live";
import { AppRoutes } from "./app/AppRoutes";
import { FailureBoundary } from "./app/FailureBoundary";
import { createAppQueryClient } from "./app/queryClient";

function LiveUpdates() {
  useLiveUpdates();
  return null;
}

export function App() {
  const [queryClient] = useState(createAppQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <LiveUpdates />
      <BrowserRouter>
        <FailureBoundary>
          <AppRoutes />
        </FailureBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
