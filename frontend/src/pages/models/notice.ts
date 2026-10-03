import { createContext, useContext } from "react";

export type EditorNotice = { tone: "info" | "error"; message: string };

interface NoticeApi {
  /** Raise a notice for the current URL entry; it goes away on the next navigation. */
  notify: (notice: EditorNotice) => void;
  clear: () => void;
}

/** The Models page owns the one toast region; its tabs and the editor raise notices through this. */
export const NoticeContext = createContext<NoticeApi>({
  notify: () => undefined,
  clear: () => undefined,
});
export const useNotice = () => useContext(NoticeContext);
