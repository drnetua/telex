import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiFailure } from "../../api/client";
import { canCreatePasskey, createPasskey, PasskeyCancelled } from "../../api/webauthn";
import { createAppQueryClient } from "../../app/queryClient";
import { AppShell } from "../../shell/AppShell/AppShell";
import { ProfileSecurityPage } from "./ProfileSecurityPage";

vi.mock("../../api/webauthn", async (orig) => ({
  ...(await orig<object>()),
  canCreatePasskey: vi.fn().mockResolvedValue(true),
  createPasskey: vi.fn().mockResolvedValue(undefined),
}));

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const me = {
  ownerId: "o1",
  email: "me@example.com",
  linkedAccountCount: 0,
  theme: "light",
  timeZone: null,
  timeZoneIsFallback: false,
};
const passkey = {
  id: "p1",
  label: "Safari on iPhone",
  createdAt: "2026-10-02T08:00:00Z",
  lastUsedAt: null,
};
const sessions = {
  items: [
    {
      id: "s1",
      userAgentLabel: "Chrome on Mac",
      deviceType: "computer",
      startedAt: "2026-10-01T08:00:00Z",
      lastActivityAt: new Date().toISOString(),
      current: true,
    },
    {
      id: "s2",
      userAgentLabel: "Safari on iPhone",
      deviceType: "phone",
      startedAt: "2026-10-01T08:00:00Z",
      lastActivityAt: new Date().toISOString(),
      current: false,
    },
  ],
};

/** Routes fetches by method + url; later passkey/session lists can be replaced per call. */
function stubApi(opts: {
  passkeys?: unknown[];
  sessionList?: unknown;
  onCall?: (method: string, url: string) => Response | Promise<Response> | undefined;
}) {
  const calls: Array<[string, string]> = [];
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push([method, url]);
    const custom = opts.onCall?.(method, url);
    if (custom) return Promise.resolve(custom);
    if (url === "/api/v1/me") return Promise.resolve(json(200, me));
    if (url === "/api/v1/passkeys" && method === "GET")
      return Promise.resolve(json(200, { items: opts.passkeys ?? [passkey] }));
    if (url === "/api/v1/sessions" && method === "GET")
      return Promise.resolve(json(200, opts.sessionList ?? sessions));
    return Promise.resolve(new Response(null, { status: 204 }));
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

function setup(entry = "/profile") {
  render(
    <QueryClientProvider client={createAppQueryClient()}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route
            path="/profile"
            element={
              <AppShell email="me@example.com">
                <ProfileSecurityPage />
              </AppShell>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.mocked(canCreatePasskey).mockResolvedValue(true);
  vi.mocked(createPasskey).mockResolvedValue(undefined);
});

const pending = () => new Promise<Response>(() => undefined);

describe("SCR-64 Profile and security", () => {
  it("shows Passkey and Session dates in the Owner's time zone", async () => {
    const calls = stubApi({
      passkeys: [{ ...passkey, createdAt: "2026-10-02T03:00:00Z" }],
      onCall: (method, url) =>
        url === "/api/v1/me" && method === "GET"
          ? json(200, { ...me, timeZone: "America/Los_Angeles" })
          : undefined,
    });
    setup();
    expect(await screen.findByText(/Created 1 Oct 2026/)).toBeInTheDocument();
    expect(calls.some(([, url]) => url.includes("detected-time-zone"))).toBe(false);
  });

  it("shows heading and signed-in email", async () => {
    stubApi({});
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "Profile and security" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Signed in as me@example.com")).toBeInTheDocument();
  });

  it("AC-179: shows the Appearance card with the saved theme and the System hint", async () => {
    stubApi({});
    setup();
    const group = await screen.findByRole("radiogroup", { name: "Theme" });
    await waitFor(() => expect(within(group).getByRole("radio", { name: "Light" })).toBeChecked());
    expect(within(group).getAllByRole("radio")).toHaveLength(3);
    expect(screen.getByRole("heading", { level: 2, name: "Appearance" })).toBeInTheDocument();
    expect(
      screen.getByText("System follows your device's light or dark mode."),
    ).toBeInTheDocument();
  });

  it("AC-89: lists passkey with label and Never used", async () => {
    stubApi({});
    setup();
    expect(
      await screen.findByRole("heading", { name: "Safari on iPhone", level: 4 }),
    ).toBeVisible();
    expect(screen.getByText(/Never used/)).toBeInTheDocument();
    expect(screen.getByText(/Created/)).toBeInTheDocument();
  });

  it("AC-91: no passkeys shows empty state with Add a passkey", async () => {
    stubApi({ passkeys: [] });
    setup();
    expect(await screen.findByText("No passkeys yet.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add a passkey" })).toBeEnabled();
  });

  it("AC-92: remove asks for confirmation, then deletes and refetches", async () => {
    const calls = stubApi({});
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Remove" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Remove this passkey?")).toBeInTheDocument();
    expect(dialog).toHaveTextContent("Sessions already open stay open.");
    expect(calls.some(([m]) => m === "DELETE")).toBe(false);
    await userEvent.click(within(dialog).getByRole("button", { name: "Remove passkey" }));
    await waitFor(() => expect(calls).toContainEqual(["DELETE", "/api/v1/passkeys/p1"]));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByRole("heading", { level: 2, name: "Passkeys" })).toHaveFocus();
  });

  it("AC-92: keeping the passkey sends no request", async () => {
    const calls = stubApi({});
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Remove" }));
    await userEvent.click(await screen.findByRole("button", { name: "Keep passkey" }));
    expect(calls.some(([m]) => m === "DELETE")).toBe(false);
  });

  it("AC-93: sessions show device, This device badge and End session only on others", async () => {
    stubApi({});
    setup();
    expect(await screen.findByText("This device")).toBeInTheDocument();
    expect(screen.getByText(/Computer · Active/)).toBeInTheDocument();
    expect(screen.getByText(/Phone · Active/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "End session" })).toHaveLength(1);
  });

  it("AC-93/AC-97: ending a session acts immediately; 404 is treated as done", async () => {
    const calls = stubApi({
      onCall: (m, u) =>
        m === "DELETE" && u === "/api/v1/sessions/s2"
          ? json(404, { code: "not-found" })
          : undefined,
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "End session" }));
    await waitFor(() => expect(calls).toContainEqual(["DELETE", "/api/v1/sessions/s2"]));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("AC-94: Sign out of all other sessions posts end-others", async () => {
    const calls = stubApi({});
    setup();
    await userEvent.click(
      await screen.findByRole("button", { name: "Sign out of all other sessions" }),
    );
    await waitFor(() => expect(calls).toContainEqual(["POST", "/api/v1/sessions/end-others"]));
  });

  it("AC-94: only this device hides the footer button", async () => {
    stubApi({ sessionList: { items: [sessions.items[0]] } });
    setup();
    await screen.findByText("This device");
    expect(
      screen.queryByRole("button", { name: "Sign out of all other sessions" }),
    ).not.toBeInTheDocument();
  });
});

describe("SCR-64 passkey states (AC-89, AC-91, AC-97)", () => {
  it("passkeys-unsupported replaces Add with the note, keeping the empty sentence", async () => {
    vi.mocked(canCreatePasskey).mockResolvedValue(false);
    stubApi({ passkeys: [] });
    setup();
    expect(await screen.findByText("No passkeys yet.")).toBeInTheDocument();
    expect(
      await screen.findByText(
        "This browser doesn't support passkeys. Add one from another device.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add a passkey" })).not.toBeInTheDocument();
  });

  it("adding shows a busy 'Waiting for your device' button", async () => {
    let finish!: () => void;
    vi.mocked(createPasskey).mockReturnValue(new Promise<void>((r) => (finish = r)));
    stubApi({ passkeys: [] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Add a passkey" }));
    expect(screen.getByRole("button", { name: "Waiting for your device" })).toBeDisabled();
    finish();
  });

  it("add-cancelled returns to the previous state with no message", async () => {
    vi.mocked(createPasskey).mockRejectedValue(new PasskeyCancelled());
    stubApi({ passkeys: [] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Add a passkey" }));
    expect(await screen.findByRole("button", { name: "Add a passkey" })).toBeEnabled();
    expect(screen.queryByText("No passkey was created. Try again.")).not.toBeInTheDocument();
  });

  it("add-failed shows the error toast", async () => {
    vi.mocked(createPasskey).mockRejectedValue(new ApiFailure(400, "passkey-registration-failed"));
    stubApi({ passkeys: [] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Add a passkey" }));
    expect(await screen.findByText("No passkey was created. Try again.")).toBeInTheDocument();
  });

  it("added refetches the list and the new row shows Never used", async () => {
    const list: unknown[] = [];
    vi.mocked(createPasskey).mockImplementation(async () => {
      list.push(passkey);
    });
    stubApi({ passkeys: list });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Add a passkey" }));
    expect(
      await screen.findByRole("heading", { name: "Safari on iPhone", level: 4 }),
    ).toBeVisible();
    expect(screen.getByText(/Never used/)).toBeInTheDocument();
  });

  it("removing shows a busy 'Removing' confirm button", async () => {
    stubApi({
      onCall: (m, u) => (m === "DELETE" && u === "/api/v1/passkeys/p1" ? pending() : undefined),
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Remove" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Remove passkey" }));
    expect(await within(dialog).findByRole("button", { name: "Removing" })).toBeDisabled();
  });

  it("removed-on-404 closes the dialog and refetches the list", async () => {
    const calls = stubApi({
      onCall: (m, u) =>
        m === "DELETE" && u === "/api/v1/passkeys/p1"
          ? json(404, { code: "not-found" })
          : undefined,
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Remove" }));
    await userEvent.click(
      within(await screen.findByRole("dialog")).getByRole("button", { name: "Remove passkey" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() =>
      expect(calls.filter(([m, u]) => m === "GET" && u === "/api/v1/passkeys")).toHaveLength(2),
    );
  });

  it("AC-98: the #sessions anchor scrolls the sessions card into view", async () => {
    const scroll = vi.fn();
    Element.prototype.scrollIntoView = scroll;
    stubApi({});
    setup("/profile#sessions");
    await screen.findByText("This device");
    await waitFor(() => expect(scroll).toHaveBeenCalled());
    expect(document.getElementById("sessions")).not.toBeNull();
  });
});

describe("SCR-64 Time zone card", () => {
  const meWith = (extra: object) => (m: string, u: string) =>
    u === "/api/v1/me" && m === "GET" ? json(200, { ...me, ...extra }) : undefined;
  const zoneList = { items: ["Europe/Berlin", "Europe/Kyiv", "UTC"] };
  const isPatch = ([m, u]: [string, string]) => m === "PATCH" && u === "/api/v1/me/preferences";
  const pickerName = { name: "Choose your time zone" };

  it("AC-183: shows the zone with its offset and no hint when it was detected", async () => {
    stubApi({ onCall: meWith({ timeZone: "Europe/Kyiv" }) });
    setup();
    expect(await screen.findByRole("heading", { level: 4, name: "Kyiv" })).toBeInTheDocument();
    expect(screen.getByText(/^Europe\/Kyiv \u00b7 UTC[+-]\d\d:\d\d$/)).toBeInTheDocument();
    expect(
      screen.getByText("Dates and times in teleX use this time zone on all your devices."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/couldn't read your device's time zone/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change time zone" })).toBeInTheDocument();
  });

  it("AC-183: shows UTC and the hint with Choose yours only when the zone is a fallback", async () => {
    stubApi({
      onCall: (m, u) =>
        u === "/api/v1/time-zones"
          ? json(200, zoneList)
          : meWith({ timeZone: "UTC", timeZoneIsFallback: true })(m, u),
    });
    setup();
    expect(
      await screen.findByText("We couldn't read your device's time zone, so teleX uses UTC."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Choose yours" }));
    expect(await screen.findByRole("dialog", pickerName)).toBeInTheDocument();
  });

  it("AC-184: fetches the list only on open, searches, saves, toasts and clears the hint", async () => {
    let saved = false;
    const calls = stubApi({
      onCall: (m, u) => {
        if (u === "/api/v1/time-zones") return json(200, zoneList);
        if (u === "/api/v1/me/preferences" && m === "PATCH") {
          saved = true;
          return json(200, { theme: "light", timeZone: "Europe/Kyiv", timeZoneIsFallback: false });
        }
        return meWith(
          saved ? { timeZone: "Europe/Kyiv" } : { timeZone: "UTC", timeZoneIsFallback: true },
        )(m, u);
      },
    });
    setup();
    await screen.findByText(/couldn't read your device's time zone/);
    expect(calls.some(([, u]) => u === "/api/v1/time-zones")).toBe(false);
    await userEvent.click(screen.getByRole("button", { name: "Change time zone" }));
    const dialog = await screen.findByRole("dialog", pickerName);
    await userEvent.type(within(dialog).getByRole("combobox"), "kyiv");
    await userEvent.click(await within(dialog).findByRole("option", { name: /Kyiv/ }));
    await waitFor(() => expect(screen.queryByRole("dialog", pickerName)).toBeNull());
    expect(await screen.findByRole("heading", { level: 4, name: "Kyiv" })).toBeInTheDocument();
    expect(await screen.findByText("Time zone saved.")).toBeInTheDocument();
    expect(screen.queryByText(/couldn't read your device's time zone/)).not.toBeInTheDocument();
    expect(calls.filter(isPatch)).toHaveLength(1);
  });

  it("AC-185: no match says so and keeps the current zone", async () => {
    const calls = stubApi({
      onCall: (m, u) =>
        u === "/api/v1/time-zones"
          ? json(200, zoneList)
          : meWith({ timeZone: "Europe/Berlin" })(m, u),
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Change time zone" }));
    const dialog = await screen.findByRole("dialog", pickerName);
    await userEvent.type(await within(dialog).findByRole("combobox"), "Atlantis");
    expect(within(dialog).getByText(/No time zone matches/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog", pickerName)).toBeNull();
    expect(screen.getByRole("heading", { level: 4, name: "Berlin" })).toBeInTheDocument();
    expect(calls.some(isPatch)).toBe(false);
  });

  it("AC-186: a server refusal keeps the zone and says to choose from the list", async () => {
    stubApi({
      onCall: (m, u) => {
        if (u === "/api/v1/time-zones") return json(200, zoneList);
        if (u === "/api/v1/me/preferences" && m === "PATCH")
          return json(400, {
            code: "validation-failed",
            errors: [{ field: "timeZone", code: "unknown-time-zone" }],
          });
        return meWith({ timeZone: "Europe/Berlin" })(m, u);
      },
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Change time zone" }));
    await userEvent.click(await screen.findByRole("option", { name: /Kyiv/ }));
    expect(await screen.findByText("Choose a time zone from the list.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Berlin" })).toBeInTheDocument();
    expect(screen.queryByText("Time zone saved.")).not.toBeInTheDocument();
  });

  it("no answer keeps the zone, shows an error toast and Try again re-sends", async () => {
    let attempts = 0;
    const calls = stubApi({
      onCall: (m, u) => {
        if (u === "/api/v1/time-zones") return json(200, zoneList);
        if (u === "/api/v1/me/preferences" && m === "PATCH") {
          attempts += 1;
          return attempts === 1
            ? json(500, { code: "internal-error" })
            : json(200, { theme: "light", timeZone: "Europe/Kyiv", timeZoneIsFallback: false });
        }
        return meWith({ timeZone: "Europe/Berlin" })(m, u);
      },
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Change time zone" }));
    await userEvent.click(await screen.findByRole("option", { name: /Kyiv/ }));
    expect(await screen.findByText("Your time zone wasn't saved.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 4, name: "Berlin" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    await waitFor(() => expect(calls.filter(isPatch)).toHaveLength(2));
    expect(await screen.findByRole("heading", { level: 4, name: "Kyiv" })).toBeInTheDocument();
  });
});
