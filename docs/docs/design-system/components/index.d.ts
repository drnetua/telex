/** teleX components. Globals: window.TeleX. Built on Tabler tokens. */

export interface IconProps { name: string; size?: number; stroke?: number; label?: string }
export declare function Icon(props: IconProps): JSX.Element;

export interface ButtonProps { variant?: 'primary'|'secondary'|'ghost'|'danger'|'danger-outline'; size?: 'md'|'sm'; icon?: string; kbd?: string; block?: boolean; disabled?: boolean; ariaLabel?: string; onClick?: () => void; children?: React.ReactNode }
export declare function Button(props: ButtonProps): JSX.Element;

export interface BadgeProps { tone?: 'neutral'|'ai'|'success'|'warning'|'danger'|'info'; icon?: string; children: React.ReactNode }
export declare function Badge(props: BadgeProps): JSX.Element;

export interface AvatarProps { name?: string; icon?: string; hue?: number; size?: 'md'|'sm' }
export declare function Avatar(props: AvatarProps): JSX.Element;

export interface ChipProps { icon?: string; muted?: boolean; onRemove?: false | (() => void); children: string }
export declare function Chip(props: ChipProps): JSX.Element;

export interface AppShellProps { active?: 'overview'|'inbox'|'chats'|'assistants'|'runs'|'tasks'|'settings'; inboxCount?: number; layout?: 'desktop'|'phone'; title?: string; accounts?: React.ReactNode; banner?: React.ReactNode; stopped?: boolean; children?: React.ReactNode }
export declare function AppShell(props: AppShellProps): JSX.Element;

export interface AccountSwitcherProps { accounts: {id: string; name: string}[]; value?: string; onChange?: (id: string) => void }
export declare function AccountSwitcher(props: AccountSwitcherProps): JSX.Element;

export interface StopAllButtonProps { stopped?: boolean; onStop?: () => void; onResume?: () => void }
export declare function StopAllButton(props: StopAllButtonProps): JSX.Element;

export interface StatusBannerProps { kind?: 'paused'|'budget'|'account'|'bot'|'consent'|'triage'; kinds?: string[]; text?: string; action?: string }
export declare function StatusBanner(props: StatusBannerProps): JSX.Element;

export interface SidePanelProps { title: string; layout?: 'desktop'|'phone'; onClose?: () => void; children?: React.ReactNode }
export declare function SidePanel(props: SidePanelProps): JSX.Element;

export interface ChatRowProps { name: string; preview?: string; time?: string; unread?: number; trace?: number; private?: boolean; account?: string; selected?: boolean; disabled?: boolean; disabledReason?: string; hue?: number }
export declare function ChatRow(props: ChatRowProps): JSX.Element;

export interface MessageProps { text?: string; time?: string; out?: boolean; author?: string; kind?: 'text'|'deleted'|'blocked'; media?: 'photo'|'video'|'file'|'generated'|'unavailable'; replyTo?: string; forwarded?: string; edited?: boolean; byAssistant?: string; trace?: number; compact?: boolean }
export declare function Message(props: MessageProps): JSX.Element;

export interface AiTraceProps { count?: number; state?: 'deferred'; onClick?: () => void }
export declare function AiTrace(props: AiTraceProps): JSX.Element;

export interface ComposerProps { state?: 'empty'|'sending'|'error'|'readonly'; value?: string; replyTo?: string; retryIn?: number }
export declare function Composer(props: ComposerProps): JSX.Element;

export interface MediaThumbProps { kind?: 'photo'|'video'|'file'|'generated'|'unavailable'; label?: string }
export declare function MediaThumb(props: MediaThumbProps): JSX.Element;

export interface InboxCardProps { kind?: 'draft'|'doubt'|'blocked'|'note'|'task'; assistant?: string; chat?: string; time?: string; ttl?: number; original?: string; proposal?: string; reason?: string; generated?: boolean; focused?: boolean }
export declare function InboxCard(props: InboxCardProps): JSX.Element;

export interface VerdictProps { value?: 'yes'|'likely'|'unsure'|'no'; confidence?: number }
export declare function Verdict(props: VerdictProps): JSX.Element;

export interface TtlCountdownProps { minutes: number; stale?: boolean; state?: 'ok'|'soon'|'stale'|'expired' }
export declare function TtlCountdown(props: TtlCountdownProps): JSX.Element;

export interface UndoBarProps { seconds?: number; live?: boolean; state?: 'cancelled'|'sent'; chat?: string }
export declare function UndoBar(props: UndoBarProps): JSX.Element;

export interface FeedbackToggleProps { value?: 'yes'|'no'|null; trained?: number }
export declare function FeedbackToggle(props: FeedbackToggleProps): JSX.Element;

export interface AssistantCardProps { name: string; summary?: string; state?: 'running'|'paused'|'stopped'|'budget'|'model'|'account'|'consent'; runs?: number; trained?: number; doubts?: number; spent?: number; selectable?: boolean; selected?: boolean }
export declare function AssistantCard(props: AssistantCardProps): JSX.Element;

export interface AutonomyPickerProps { value?: 'observe'|'suggest'|'act'; progress?: number }
export declare function AutonomyPicker(props: AutonomyPickerProps): JSX.Element;

export interface ScopePickerProps { sets?: string[]; privateWarning?: boolean }
export declare function ScopePicker(props: ScopePickerProps): JSX.Element;

export interface TriggerEditorProps { type?: 'words'|'meaning'|'hybrid'|'schedule'|'chain'|'command'; text?: string; split?: boolean }
export declare function TriggerEditor(props: TriggerEditorProps): JSX.Element;

export interface SensitivitySliderProps { value?: number }
export declare function SensitivitySlider(props: SensitivitySliderProps): JSX.Element;

export interface ScheduleEditorProps { repeat?: 'daily'|'weekdays'|'weekly'; time?: string }
export declare function ScheduleEditor(props: ScheduleEditorProps): JSX.Element;

export interface ModelProfilePickerProps { value?: string; fallback?: boolean }
export declare function ModelProfilePicker(props: ModelProfilePickerProps): JSX.Element;

export interface BudgetFieldProps { period?: 'run'|'day'|'month'; limit?: number; spent?: number }
export declare function BudgetField(props: BudgetFieldProps): JSX.Element;

export interface RunStatusProps { status?: 'queued'|'thinking'|'waiting'|'done'|'blocked'|'failed'|'cancelled'|'dry' }
export declare function RunStatus(props: RunStatusProps): JSX.Element;

export interface RunTimelineProps { steps?: [title: string, detail: string, state: 'done'|'blocked'|'waiting'][] }
export declare function RunTimeline(props: RunTimelineProps): JSX.Element;

export interface TestResultsProps { state?: 'running'|'done'|'stale'|'thin' }
export declare function TestResults(props: TestResultsProps): JSX.Element;

export interface CostProps { value: number; estimate?: boolean }
export declare function Cost(props: CostProps): JSX.Element;

export interface FilterBarProps { filters?: [label: string, value: string][] }
export declare function FilterBar(props: FilterBarProps): JSX.Element;

export interface DataTableProps { columns?: string[]; rows?: (string|number)[][] }
export declare function DataTable(props: DataTableProps): JSX.Element;

export interface ChatPickerProps { single?: boolean }
export declare function ChatPicker(props: ChatPickerProps): JSX.Element;

export interface OnboardingStepsProps { steps?: string[]; current?: number; skippable?: boolean }
export declare function OnboardingSteps(props: OnboardingStepsProps): JSX.Element;

export interface CodeInputProps { state?: 'input'|'invalid'|'limited'; value?: string; resendIn?: number }
export declare function CodeInput(props: CodeInputProps): JSX.Element;

export interface ConfirmDialogProps { variant?: 'default'|'destructive'; title?: string; consequence?: string; confirm?: string }
export declare function ConfirmDialog(props: ConfirmDialogProps): JSX.Element;

export interface EmptyStateProps { kind?: 'first'|'done'|'none'|'blocked'|'thin'; text?: string; action?: string }
export declare function EmptyState(props: EmptyStateProps): JSX.Element;

export interface LoadStateProps { state?: 'loading'|'error'|'offline'; rows?: number }
export declare function LoadState(props: LoadStateProps): JSX.Element;

export interface ToastProps { kind?: 'info'|'success'|'error'|'draft'; text?: string; action?: string }
export declare function Toast(props: ToastProps): JSX.Element;

export interface BotMessageProps { state?: 'buttons'|'plain'|'done'|'pinned'; assistant?: string; account?: string; text?: string; buttons?: string[][] }
export declare function BotMessage(props: BotMessageProps): JSX.Element;

export interface KpiTileProps { label: string; value?: string; delta?: number; better?: 'up'|'down'; trend?: number[]; compare?: string; href?: string; empty?: boolean }
export declare function KpiTile(props: KpiTileProps): JSX.Element;

export interface TimeChartProps { title?: string; subtitle?: string; labels?: string[]; series?: {name: string; data: number[]; slot?: number}[]; mode?: 'stacked'|'line'; unit?: ''|'$'|'s'|'%'; budget?: number }
export declare function TimeChart(props: TimeChartProps): JSX.Element;

export interface BreakdownProps { items?: {name: string; value: number; slot?: number}[]; unit?: ''|'$'; title?: string; subtitle?: string; dimension?: string }
export declare function Breakdown(props: BreakdownProps): JSX.Element;

export interface PeriodPickerProps { value?: '24h'|'7d'|'30d'|'custom'; compare?: boolean; tz?: string }
export declare function PeriodPicker(props: PeriodPickerProps): JSX.Element;
