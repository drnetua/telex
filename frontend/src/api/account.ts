import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { apiFetch } from "./client";

export interface Me {
  ownerId: string;
  email: string;
  linkedAccountCount: number;
}

export const meKey = ["me"] as const;

/** The SPA's session state. */
export function useMe() {
  return useQuery({ queryKey: meKey, queryFn: () => apiFetch<Me>("/api/v1/me") });
}

export function useSignOut() {
  const client = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/sign-out", { method: "POST" }),
    onSuccess: () => {
      client.clear();
      void navigate("/sign-in", { replace: true });
    },
  });
}
