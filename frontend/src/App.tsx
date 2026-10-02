import { QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { BrowserRouter } from "react-router";
import { AppRoutes } from "./app/AppRoutes";
import { FailureBoundary } from "./app/FailureBoundary";
import { createAppQueryClient } from "./app/queryClient";

export function App() {
  const [queryClient] = useState(createAppQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <FailureBoundary>
          <AppRoutes />
        </FailureBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
