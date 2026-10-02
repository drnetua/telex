# Graph Report - telex  (2026-10-02)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2065 nodes · 4136 edges · 231 communities (75 shown, 156 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 390 edges (avg confidence: 0.84)
- Token cost: 7,295 input · 6,525 output

## Graph Freshness
- Built from commit: `08c0fe04`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Sign-in Integration Tests
- Passkey Integration Tests
- Sign-in REST Controller
- Spring Security Configuration
- Identity Module Tasks
- Passkey API Tests
- Telex UI Components
- Design System Components
- React App Routing
- Sign-in Security Tests
- Mail Failure Tests
- UI Component Types
- Architecture Documentation
- E2E Test Configuration
- Identity System Design
- Database Schema Tests
- React UI Components
- Auth Navigation Logic
- Project Test Planning
- Sign-in Frontend Logic
- Session Management Models
- Session API Tests
- Passkey Security Config
- Sign-in Issuance Tests
- Component Previews
- Sign-in Secret Hashing
- Typed ID Utilities
- Sign-in Grant Repository
- Sign-in Business Logic
- WebAuthn Authenticator Simulation
- Frontend Linting Config
- Sign-in Notification Tests
- Frontend Dev Dependencies
- Session Service Logic
- Frontend TypeScript Config
- Session Persistence Layer
- System Module Architecture
- WebAuthn Frontend Client
- Session REST Controller
- Sign-in Email Service
- Owner Management Service
- Passkey Management Service
- Passkey REST Controller
- User Agent Parsing
- Global Error Handling
- UI Screen Mockups
- AI Agent Logic
- Email Delivery Logic
- Chat UI Components
- Application Feature Map
- Project Roadmap Milestones
- SPA Static Hosting
- E2E TypeScript Config
- Passkey Repository Config
- NPM Script Commands
- Passkey Persistence Layer
- SMTP Mail Implementation
- API Problem Details
- Current User Controller
- WebAuthn Relying Party
- Gradle Test Conventions
- AI Decision Pipeline
- Assistant Creation Flow
- Chat Interaction Flow
- Confirmation Dialog Component
- Modularity Verification Tests
- Deployment Configuration
- Application Smoke Tests
- Spring Boot Entrypoint
- SMTP Error Tests
- Authentication Methods Overview
- AI Configuration UI
- Session Database Migration
- Passkey Database Migration
- Session SQL Schema
- Passkey SQL Schema
- Frontend Entrypoint
- Coolify Deployment Script
- Knowledge Graph Script
- Baseline Database Migration
- Grant Database Migration
- Infrastructure Stack
- Data Visualization UI
- UI Design Standards
- Input Component Previews
- Data Display Previews
- Grant SQL Schema
- Project Metadata
- Smoke Test Script
- Owner Database Migration
- KPI Dashboard Icons
- Dashboard Control Components
- Feedback UI Components
- Verdict UI Components
- LLM Agent Architecture
- API Contract Sync
- Owner Database Schema
- Vite Frontend Configuration
- Telegram Tdlib Facade
- AI Review Agent Documentation
- User Role Models
- Telex Brand Assets
- Agent Execution Model
- Project Roadmap and Flows
- Cost Preview Component
- Brand Cover Assets
- Feedback Toggle Component
- Scope Picker Component
- Sensitivity Slider Component
- Side Panel Component
- Status Banner Component
- Stop All Button
- Test Results Component
- Time Chart Component
- Toast Notification Component
- Trigger Editor Component
- TTL Countdown Component
- Undo Bar Component
- Approval Workflow Design
- Platform Review Findings
- System Error Pages
- Prettier Formatting Config
- Knowledge Graph Workflow
- Detekt Static Analysis
- Activity Status Icon
- Alert Circle Icon
- Alert Triangle Icon
- Undo Arrow Icon
- Redo Arrow Icon
- Ban Action Icon
- Notification Bell Icon
- Settings Bolt Icon
- Telegram Brand Icon
- Idea Bulb Icon
- Calendar Date Icon
- Bar Chart Icon
- Check Mark Icon
- Checklist Task Icon
- Chevron Down Icon
- Chevron Right Icon
- Circle Check Icon
- Clock Time Icon
- Source Code Icon
- CPU Processor Icon
- Currency Dollar Icon
- Vertical Dots Icon
- File Download Icon
- Visibility Eye Icon
- Document File Icon
- Data Filter Icon
- Stop Hand Icon
- Help Circle Icon
- Activity History Icon
- Hourglass Wait Icon
- Message Inbox Icon
- Info Circle Icon
- Dashboard Layout Icon
- Security Lock Icon
- Logout Action Icon
- Navigation Menu Icon
- Message Circle Icon
- Chat Messages Icon
- Minus Remove Icon
- Attachment Paperclip Icon
- Edit Pencil Icon
- Photo Image Icon
- Player Pause Icon
- Media Playback Icon
- Add Action Icon
- Reload Action Icon
- Search Interface Icon
- Selection UI Icon
- Message Send Icon
- Configuration Settings Icon
- Security Privacy Icon
- AI Enhancement Icon
- Goal Tracking Icon
- Negative Feedback Icon
- Positive Feedback Icon
- Delete Action Icon
- Single Profile Icon
- Group Profile Icon
- Video Media Icon
- Connectivity Status Icon
- Close Dismiss Icon
- Tooling Scope Validation
- Task Management Epic

## God Nodes (most connected - your core abstractions)
1. `OwnerId` - 39 edges
2. `Icon()` - 36 edges
3. `Icon()` - 36 edges
4. `SignInApiIT` - 31 edges
5. `PasskeyCeremoniesIT` - 29 edges
6. `SessionsApiIT` - 28 edges
7. `SignInIssueIT` - 28 edges
8. `SignInSessions` - 27 edges
9. `SignInSessionsIT` - 26 edges
10. `SignInRedeemIT` - 25 edges

## Surprising Connections (you probably didn't know these)
- `Coolify deploy compose.yaml` --semantically_similar_to--> `Root compose.yaml`  [INFERRED] [semantically similar]
  deploy/coolify/compose.yaml → compose.yaml
- `Vite SPA entry index.html (#root, main.tsx)` --conceptually_related_to--> `teleX screens index`  [INFERRED]
  frontend/index.html → docs/teleX-screens/index.html
- `CONTEXT.md domain glossary` --references--> `Tech spec`  [EXTRACTED]
  CONTEXT.md → docs/docs/01-tech-spec.md
- `pnpm workspace (frontend, e2e)` --references--> `Vite SPA entry index.html (#root, main.tsx)`  [INFERRED]
  pnpm-workspace.yaml → frontend/index.html
- `CI workflow` --references--> `Root compose.yaml`  [INFERRED]
  .github/workflows/ci.yml → compose.yaml

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Four ACL integration modules** — docs_docs_img_architecture_modules_telegram, docs_docs_img_architecture_modules_llm, docs_docs_img_architecture_modules_decision, docs_docs_img_architecture_modules_bot [EXTRACTED 1.00]
- **Work shell navigation sections** — docs_docs_img_app_map_overview, docs_docs_img_app_map_inbox, docs_docs_img_app_map_chats, docs_docs_img_app_map_agents, docs_docs_img_app_map_runs, docs_docs_img_app_map_tasks, docs_docs_img_app_map_settings [EXTRACTED 1.00]
- **Core modules communicating via Modulith events** — docs_docs_img_architecture_modules_identity, docs_docs_img_architecture_modules_messaging, docs_docs_img_architecture_modules_triage, docs_docs_img_architecture_modules_agents, docs_docs_img_architecture_modules_tools, docs_docs_img_architecture_modules_tasks, docs_docs_img_architecture_modules_scheduling, docs_docs_img_architecture_modules_audit [EXTRACTED 1.00]
- **teleX core spec documents** — docs_docs_01_tech_spec, docs_docs_02_epics, docs_docs_03_product_spec [EXTRACTED 1.00]
- **F2 create-assistant wizard steps** — docs_docs_img_flow_f2_create_assistant_step_template, docs_docs_img_flow_f2_create_assistant_step_what_to_read, docs_docs_img_flow_f2_create_assistant_step_when, docs_docs_img_flow_f2_create_assistant_step_what_to_do, docs_docs_img_flow_f2_create_assistant_step_test, docs_docs_img_flow_f2_create_assistant_step_enable [EXTRACTED 1.00]
- **F3 draft approval flow** — docs_docs_img_flow_f3_draft_approval_counterpart_message, docs_docs_img_flow_f3_draft_approval_triage_assessment, docs_docs_img_flow_f3_draft_approval_draft_and_task, docs_docs_img_flow_f3_draft_approval_owner_notification, docs_docs_img_flow_f3_draft_approval_send_as_owner [EXTRACTED 1.00]
- **Foundation ADRs 0001-0004** — docs_adr_0001_kotlin_spring_modulith_postgres_react_stack, docs_adr_0002_single_app_with_isolated_tdlib_subproject, docs_adr_0003_postgres_jdbc_flyway_uuidv7_persistence, docs_adr_0004_quartz_jdbc_scheduler [EXTRACTED 1.00]
- **SCR-80 Overview mockup variants** — docs_designs_scr_80_overview_main_dc, docs_designs_scr_80_overview_dark_dc, docs_designs_scr_80_overview_empty_dc [EXTRACTED 1.00]
- **Platform-skeleton sign-in ADRs** — docs_features_platform_skeleton_adr_0001_keep_sign_in_sessions_in_an_identity_owned_table_behind_an_opaque_cookie, docs_features_platform_skeleton_adr_0002_use_spring_security_webauthn_for_passkeys, docs_features_platform_skeleton_adr_0003_redeem_sign_in_link_and_code_as_one_hashed_single_use_grant, docs_features_platform_skeleton_adr_0004_send_email_through_a_new_mail_integration_module, docs_features_platform_skeleton_adr_0005_count_session_activity_only_from_requests_the_spa_does_not_mark_as_background, docs_features_platform_skeleton_adr_0006_derive_links_cookie_security_and_passkey_rp_id_from_one_public_url [EXTRACTED 1.00]
- **Settings derived from the public URL** — docs_features_platform_skeleton_adr_0006_derive_links_cookie_security_and_passkey_rp_id_from_one_public_url_telex_public_url_setting, docs_features_platform_skeleton_adr_0006_derive_links_cookie_security_and_passkey_rp_id_from_one_public_url_webauthn_rp_id, docs_features_platform_skeleton_spec_passwordless_sign_in_by_link_and_code_us_01 [EXTRACTED 1.00]
- **Roadmap gates G0 to G4** — docs_docs_img_roadmap_g0, docs_docs_img_roadmap_g1, docs_docs_img_roadmap_g2, docs_docs_img_roadmap_g3, docs_docs_img_roadmap_g4 [EXTRACTED 1.00]
- **System 1 triage pipeline** — docs_docs_img_concept_system1_system2_tdlib_event, docs_docs_img_concept_system1_system2_prefilter, docs_docs_img_concept_system1_system2_jev_triage [EXTRACTED 1.00]
- **System 2 generation pipeline** — docs_docs_img_concept_system1_system2_llm_agent, docs_docs_img_concept_system1_system2_guardrail, docs_docs_img_concept_system1_system2_action [EXTRACTED 1.00]
- **Telex Dashboard UI Previews** — docs_design_system_previews_breakdown_light, docs_design_system_previews_kpitiyle_light, docs_design_system_previews_timechart_dark, docs_design_system_previews_timechart_light [EXTRACTED 1.00]
- **Telex Design System Icons** — docs_design_system_assets_icons_activity, docs_design_system_assets_icons_alert_circle, docs_design_system_assets_icons_alert_triangle, docs_design_system_assets_icons_arrow_back_up, docs_design_system_assets_icons_arrow_forward_up, docs_design_system_assets_icons_ban, docs_design_system_assets_icons_bell, docs_design_system_assets_icons_bolt, docs_design_system_assets_icons_brand_telegram, docs_design_system_assets_icons_bulb, docs_design_system_assets_icons_calendar, docs_design_system_assets_icons_chart_bar, docs_design_system_assets_icons_check, docs_design_system_assets_icons_checklist, docs_design_system_assets_icons_chevron_down, docs_design_system_assets_icons_chevron_right, docs_design_system_assets_icons_circle_check, docs_design_system_assets_icons_clock, docs_design_system_assets_icons_code, docs_design_system_assets_icons_cpu, docs_design_system_assets_icons_currency_dollar, docs_design_system_assets_icons_dots_vertical, docs_design_system_assets_icons_download, docs_design_system_assets_icons_eye, docs_design_system_assets_icons_file, docs_design_system_assets_icons_filter, docs_design_system_assets_icons_hand_stop, docs_design_system_assets_icons_help_circle, docs_design_system_assets_icons_history, docs_design_system_assets_icons_hourglass, docs_design_system_assets_icons_inbox, docs_design_system_assets_icons_info_circle, docs_design_system_assets_icons_layout_dashboard, docs_design_system_assets_icons_lock, docs_design_system_assets_icons_logout, docs_design_system_assets_icons_menu_2, docs_design_system_assets_icons_message_circle, docs_design_system_assets_icons_messages, docs_design_system_assets_icons_minus, docs_design_system_assets_icons_paperclip, docs_design_system_assets_icons_pencil, docs_design_system_assets_icons_photo, docs_design_system_assets_icons_player_pause, docs_design_system_assets_icons_player_play, docs_design_system_assets_icons_plus, docs_design_system_assets_icons_refresh, docs_design_system_assets_icons_search, docs_design_system_assets_icons_selector, docs_design_system_assets_icons_send, docs_design_system_assets_icons_settings, docs_design_system_assets_icons_shield_lock, docs_design_system_assets_icons_sparkles, docs_design_system_assets_icons_table, docs_design_system_assets_icons_target, docs_design_system_assets_icons_thumb_down, docs_design_system_assets_icons_thumb_up, docs_design_system_assets_icons_trash, docs_design_system_assets_icons_trending_down, docs_design_system_assets_icons_trending_up, docs_design_system_assets_icons_user, docs_design_system_assets_icons_users, docs_design_system_assets_icons_video, docs_design_system_assets_icons_wifi_off, docs_design_system_assets_icons_x [EXTRACTED 1.00]
- **Message processing pipeline from message to journal** — docs_docs_img_under_the_hood_new_message, docs_docs_img_under_the_hood_readable_check, docs_docs_img_under_the_hood_fast_triage, docs_docs_img_under_the_hood_agent_run, docs_docs_img_under_the_hood_safety_check, docs_docs_img_under_the_hood_autonomy_output, docs_docs_img_under_the_hood_journal [EXTRACTED 1.00]
- **Assistant configuration components** — docs_docs_design_system_components_scopepicker_readme_scopepicker, docs_docs_design_system_components_triggereditor_readme_triggereditor, docs_docs_design_system_components_sensitivityslider_readme_sensitivityslider, docs_docs_design_system_components_scheduleeditor_preview_scheduleeditor [INFERRED 0.75]
- **Assistant configuration components** — docs_docs_design_system_components_assistantcard_readme, docs_docs_design_system_components_autonomypicker_readme, docs_docs_design_system_components_budgetfield_readme [INFERRED 0.75]
- **Owner decision content components** — docs_docs_design_system_components_inboxcard_readme, docs_docs_design_system_components_message_readme, docs_docs_design_system_components_mediathumb_readme [INFERRED 0.75]
- **Run visibility components** — docs_docs_design_system_components_runstatus_readme, docs_docs_design_system_components_runtimeline_readme, docs_docs_design_system_components_kpitile_readme [INFERRED 0.75]
- **Sign-in flow screens** — docs_features_platform_skeleton_screens_scr_01_sign_in, docs_features_platform_skeleton_screens_scr_07_check_your_email, docs_features_platform_skeleton_screens_scr_08_confirm_sign_in_link, docs_features_platform_skeleton_screens_scr_09_create_a_passkey [INFERRED 0.85]

## Communities (231 total, 156 thin omitted)

### Community 0 - "Sign-in Integration Tests"
Cohesion: 0.06
Nodes (7): FixedClockConfiguration, MutableClock, SignInSessionsIT, Mail, SignInApiIT, ClockConfiguration, ClockConfigurationTest

### Community 1 - "Passkey Integration Tests"
Cohesion: 0.05
Nodes (9): PasskeysIT, Builder, TestCredentialRecords, PasskeyCeremoniesIT, Registered, absent(), neverUsed(), NeverUsedTolerantJdbcOperations (+1 more)

### Community 2 - "Sign-in REST Controller"
Cohesion: 0.06
Nodes (11): CodeBody, GrantCreated, LinkPreviewed, LinkTokenBody, RequestSignInEmail, SignedInBody, SignInController, SessionCookies (+3 more)

### Community 3 - "Spring Security Configuration"
Cohesion: 0.05
Nodes (5): CsrfCookieFilter, SecurityConfiguration, SessionCookieSecurityContextRepository, DeferredSecurityContext, PasskeyRegistrationFilterTest

### Community 4 - "Identity Module Tasks"
Cohesion: 0.07
Nodes (59): T1 Promote identity migrations, T2 Identity domain primitives, T3 Mail integration module, T4 Owner and session core, T5 Issue and preview sign-in grant, T6 Redeem sign-in link and code, T7 New sign-in notice email, T8 Web security and problem codes (+51 more)

### Community 5 - "Passkey API Tests"
Cohesion: 0.08
Nodes (4): PasskeysApiIT, Who, SecurityChainIT, SpaHostingIT

### Community 6 - "Telex UI Components"
Cohesion: 0.11
Nodes (52): AccountSwitcher(), AiTrace(), AppShell(), AssistantCard(), AutonomyPicker(), Avatar(), Badge(), Breakdown() (+44 more)

### Community 7 - "Design System Components"
Cohesion: 0.11
Nodes (52): AccountSwitcher(), AiTrace(), AppShell(), AssistantCard(), AutonomyPicker(), Avatar(), Badge(), Breakdown() (+44 more)

### Community 8 - "React App Routing"
Cohesion: 0.09
Nodes (32): useSignOut(), App(), AppRoutes(), FailureBoundary(), down(), json(), routeFetch(), setup() (+24 more)

### Community 9 - "Sign-in Security Tests"
Cohesion: 0.08
Nodes (5): SecretsHygieneIT, Mail, SignInRedeemIT, RecordingMailer, RecordingMailerConfiguration

### Community 10 - "Mail Failure Tests"
Cohesion: 0.06
Nodes (7): FailingSender, SignInMailFailureIT, ThrowingSender, ContractValidator, EmailAddress, EmailAddressTest, PublicUrlTest

### Community 11 - "UI Component Types"
Cohesion: 0.04
Nodes (46): AccountSwitcherProps, AiTraceProps, AppShellProps, AssistantCardProps, AutonomyPickerProps, AvatarProps, BadgeProps, BotMessageProps (+38 more)

### Community 12 - "Architecture Documentation"
Cohesion: 0.06
Nodes (43): CONTEXT.md domain glossary, ADR-0001 stack, Kotlin + Spring Boot 4 + Postgres + React stack, ADR-0002 single app + TDLib subproject, Rule: only telegram module imports TDLib, telegram-tdlib subproject, ADR-0003 persistence, ADR-0004 Quartz scheduler (+35 more)

### Community 13 - "E2E Test Configuration"
Cohesion: 0.09
Nodes (33): devDependencies, @axe-core/playwright, @playwright/test, @types/node, typescript, @types/node, typescript, name (+25 more)

### Community 14 - "Identity System Design"
Cohesion: 0.06
Nodes (36): Data-model audit 2026-10-02, Staged migrations: owner, sign_in_grant, sign_in_session, passkey tables, Changelog platform-skeleton, PR body platform-skeleton (E01), ADR-0001 Sign-in sessions in identity-owned table with opaque hashed cookie, ADR-0002 Spring Security WebAuthn for passkeys, ADR-0003 Sign-in link and code as one hashed single-use grant, ADR-0004 New mail integration module (+28 more)

### Community 15 - "Database Schema Tests"
Cohesion: 0.09
Nodes (3): IdentitySchemaIT, MigrationRollbackIT, TestcontainersConfiguration

### Community 16 - "React UI Components"
Cohesion: 0.17
Nodes (22): useEndOtherSessions(), Badge(), BadgeProps, Button(), ButtonProps, CodeInputProps, EmptyStateProps, Icon() (+14 more)

### Community 17 - "Auth Navigation Logic"
Cohesion: 0.08
Nodes (20): AUTH_PAGES, isAuthPage(), rememberDestination(), setup(), Where(), setup(), Where(), credential (+12 more)

### Community 18 - "Project Test Planning"
Cohesion: 0.06
Nodes (36): AC-98, MigrationRollbackIT column snapshot includes character_maximum_length, NewSignInNoticeIT asserts exactly 1 incomplete publication, T36 review fix R9 R10 task, platform-skeleton task tracker (T1-T36), Passkey (WebAuthn), Sign-in Grant, Sign-in Session (+28 more)

### Community 19 - "Sign-in Frontend Logic"
Cohesion: 0.13
Nodes (30): takeRememberedDestination(), previewSignInLink(), redeemSignInCode(), redeemSignInLink(), requestSignInEmail(), SignInGrant, landAfterSignIn(), CodeInput() (+22 more)

### Community 20 - "Session Management Models"
Cohesion: 0.10
Nodes (23): ADR-0005, deleteTolerant(), DeviceType, isBackground(), Me, meKey, Passkey, passkeysKey (+15 more)

### Community 24 - "Component Previews"
Cohesion: 0.12
Nodes (24): Icon preview, Icon component (Icon), InboxCard preview, InboxCard component (C-11), KpiTile preview, KpiTile component (C-38), LoadState preview, LoadState component (C-35) (+16 more)

### Community 25 - "Sign-in Secret Hashing"
Cohesion: 0.17
Nodes (3): Secrets, GrantIssued, SecretsTest

### Community 26 - "Typed ID Utilities"
Cohesion: 0.17
Nodes (5): TypedId, Uuid7, IdsTest, IdsTest, SampleId

### Community 27 - "Sign-in Grant Repository"
Cohesion: 0.22
Nodes (3): SignInGrantId, GrantRow, GrantRows

### Community 28 - "Sign-in Business Logic"
Cohesion: 0.23
Nodes (6): GrantRefusals, GrantRefused, LinkPreview, SignedIn, SignIn, DomainProblem

### Community 30 - "Frontend Linting Config"
Cohesion: 0.12
Nodes (18): @types/node, typescript, name, private, type, version, eslint, @eslint/js (+10 more)

### Community 32 - "Frontend Dev Dependencies"
Cohesion: 0.11
Nodes (19): devDependencies, eslint, @eslint/js, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals, jsdom, prettier (+11 more)

### Community 33 - "Session Service Logic"
Cohesion: 0.19
Nodes (6): Ended, Live, MySession, SessionResolution, SignInSessions, Unknown

### Community 34 - "Frontend TypeScript Config"
Cohesion: 0.11
Nodes (17): compilerOptions, isolatedModules, jsx, lib, module, moduleResolution, noEmit, noFallthroughCasesInSwitch (+9 more)

### Community 35 - "Session Persistence Layer"
Cohesion: 0.26
Nodes (4): SignInSessionId, ListedRow, SessionRow, SessionRows

### Community 36 - "System Module Architecture"
Cohesion: 0.15
Nodes (17): Architecture modules diagram, agents module, audit module, bot module, Core layer (Modulith events), decision module, identity module, Integrations layer (anti-corruption) (+9 more)

### Community 37 - "WebAuthn Frontend Client"
Cohesion: 0.25
Nodes (14): cancelledOr(), canCreatePasskey(), createPasskey(), CreationOptionsJson, DescriptorJson, descriptors(), PasskeyCancelled, RequestOptionsJson (+6 more)

### Community 39 - "Session REST Controller"
Cohesion: 0.23
Nodes (4): SignedInOwner, SessionItem, SessionList, SessionsController

### Community 40 - "Sign-in Email Service"
Cohesion: 0.21
Nodes (3): NewSignInNotice, Notice, Mailer

### Community 41 - "Owner Management Service"
Cohesion: 0.22
Nodes (5): OwnerId, Owners, Me, OwnerProfiles, SignInSessionStarted

### Community 44 - "Passkey REST Controller"
Cohesion: 0.21
Nodes (4): PasskeyItem, PasskeyList, PasskeysController, toItem()

### Community 47 - "UI Screen Mockups"
Cohesion: 0.21
Nodes (14): SCR-30 Assistants, SCR-31/32 Assistant builder with test, SCR-21/22 Chat with Why panel, SCR-80 Overview dark, SCR-80 Overview first day, SCR-10 Inbox, teleX screens preview index, SCR-80 Overview desktop light (+6 more)

### Community 48 - "AI Agent Logic"
Cohesion: 0.14
Nodes (13): Agent run: gathers context and decides what to do, Stop: AI does not see this, Output by autonomy level (note, draft, action), Blocked -> flag + Inbox, Under the hood: every message passes three filters before the assistant acts, Doubt -> Review inbox, Drafts -> Inbox for approval, Journal and explanations (what it saw, why, what it did, cost) (+5 more)

### Community 49 - "Email Delivery Logic"
Cohesion: 0.23
Nodes (3): SignInEmail, MailUnavailable, OutgoingEmail

### Community 50 - "Chat UI Components"
Cohesion: 0.18
Nodes (11): AI trace (C-08), ChatPicker preview, ChatPicker (C-30), ChatRow preview, ChatRow (C-06), Chip preview, Chip, Composer preview (+3 more)

### Community 51 - "Application Feature Map"
Cohesion: 0.22
Nodes (11): Admin console (separate access): SCR-70..72, Agents section: SCR-30..36 list, builder, test, YAML, models, restore, baselines, App map diagram (login in Overview, daily work in Inbox), Chats section: SCR-20..25 list, chat, why, search, media, forward, Inbox section (highlighted): SCR-10 confirm, review; 11 draft edit; today, Login and onboarding (one-time): SCR-01..06, Overview section: SCR-80 KPI, activity, spend, reliability, Runs section: SCR-40 run feed, SCR-41 run details (+3 more)

### Community 52 - "Project Roadmap Milestones"
Cohesion: 0.25
Nodes (11): Roadmap diagram: biweekly gates with demoable results, G0 gate: skeleton exists, CI green, G1 gate: Telegram login and chats in web, G2 gate: agent triggers by meaning, not keyword, G3 MVP gate: 3 demo scenarios work live, G4 gate: demo day, course retro, Phase Automation (weeks 5-6): E15, E17-E19 images, bot, approval; E20-E23 digest, chains, tasks, stop; E24-E26 protection, journal, admin, Phase Brain (weeks 3-4): E07-E10 search, scope, agents, models; E11-E14, E16 triggers, runtime, test; parallel worktree lanes (+3 more)

### Community 54 - "E2E TypeScript Config"
Cohesion: 0.20
Nodes (9): compilerOptions, module, moduleResolution, noEmit, skipLibCheck, strict, target, types (+1 more)

### Community 56 - "NPM Script Commands"
Cohesion: 0.22
Nodes (9): scripts, build, check, dev, format, lint, preview, test (+1 more)

### Community 62 - "Gradle Test Conventions"
Cohesion: 0.25
Nodes (7): dependencies, react, react-dom, react-router, @tabler/core, @tabler/icons-react, @tanstack/react-query

### Community 63 - "AI Decision Pipeline"
Cohesion: 0.25
Nodes (7): Action (reply, draft, task, approval if risky), Guardrail (JevGuardrailAdvisor, self-refine via JevJudge), Jev triage (Noul/Choice/Score, value + confidence), LLM agent (OpenRouter, agent model, tools, memory), Prefilter (agent channel scope, keywords, regex), Schedule / manual task (bypasses System 1), TDLib event (updateNewMessage normalized)

### Community 64 - "Assistant Creation Flow"
Cohesion: 0.32
Nodes (8): Flow F2: create assistant (enable only after test on real messages), Decision: Does it fit?, Step 6: Enable (works from now, autonomy Suggest), Step 1: Template (Digest, Guard, Secretary, Custom), Step 5: Test on last week's messages (hit rate and cost), Step 4: What to do (actions + autonomy, model and budget), Step 2: What to read (chat sets, private is gray), Step 3: When (words, sense, schedule, command)

### Community 65 - "Chat Interaction Flow"
Cohesion: 0.29
Nodes (8): Counterpart writes in chat, Counterpart receives ordinary reply, Draft reply plus task, Owner notification in Saved Messages with 3 buttons, Reject or TTL: nothing sent, draft archived, lesson for condition, System sends on behalf of Owner, Assessment: confident yes within a moment, Web edit screen SCR-10

### Community 66 - "Confirmation Dialog Component"
Cohesion: 0.39
Nodes (5): ConfirmDialog(), ConfirmDialogProps, Busy(), Harness(), Host()

### Community 69 - "Deployment Configuration"
Cohesion: 0.33
Nodes (4): CI workflow, Root compose.yaml, Coolify deploy compose.yaml, teleX README

### Community 73 - "Authentication Methods Overview"
Cohesion: 0.40
Nodes (6): Mailpit local mailbox, Passkey, Sign-in Code, Sign-in Link, Sign-in Session, Email magic link + passkey auth

### Community 74 - "AI Configuration UI"
Cohesion: 0.33
Nodes (6): ScheduleEditor (C-21), ScopePicker (C-18), SensitivitySlider (C-20), SidePanel (C-05), TestResults (C-26), TriggerEditor (C-19)

### Community 75 - "Session Database Migration"
Cohesion: 0.60
Nodes (3): sign_in_session, sign_in_session_key_hash_uq, sign_in_session_owner_id_idx

### Community 76 - "Passkey Database Migration"
Cohesion: 0.70
Nodes (4): user_credentials, user_credentials_user_entity_user_id_idx, user_entities, user_entities_name_uq

### Community 77 - "Session SQL Schema"
Cohesion: 0.60
Nodes (3): sign_in_session, sign_in_session_key_hash_uq, sign_in_session_owner_id_idx

### Community 78 - "Passkey SQL Schema"
Cohesion: 0.70
Nodes (4): user_credentials, user_credentials_user_entity_user_id_idx, user_entities, user_entities_name_uq

### Community 79 - "Frontend Entrypoint"
Cohesion: 0.40
Nodes (3): root, react-dom, @tabler/core

### Community 80 - "Coolify Deployment Script"
Cohesion: 0.80
Nodes (4): api(), fail(), deploy-coolify.sh script, step()

### Community 81 - "Knowledge Graph Script"
Cohesion: 0.50
Nodes (4): fail(), GEMINI_BASE_URL, GRAPHIFY_GEMINI_MODEL, graph-update.sh script

### Community 82 - "Baseline Database Migration"
Cohesion: 0.83
Nodes (3): event_publication, event_publication_by_completion_date_idx, event_publication_serialized_event_hash_idx

### Community 83 - "Grant Database Migration"
Cohesion: 0.83
Nodes (3): sign_in_grant, sign_in_grant_link_token_hash_uq, sign_in_grant_live_by_canonical_email_idx

### Community 84 - "Infrastructure Stack"
Cohesion: 0.67
Nodes (4): Postgres pgvector database, Flyway migrations with rollback scripts, UUIDv7 app-generated ids, Quartz JDBC scheduler

### Community 85 - "Data Visualization UI"
Cohesion: 0.50
Nodes (4): Table Icon, Spend Breakdown UI Preview (Light), Time Chart UI Preview (Dark), Time Chart UI Preview (Light)

### Community 86 - "UI Design Standards"
Cohesion: 0.67
Nodes (4): SCR-80 Overview screen, WCAG 2.2 AA accessibility, Tabler 1.6 UI kit, Design tokens, ai purple only for AI content

### Community 87 - "Input Component Previews"
Cohesion: 0.50
Nodes (4): CodeInput preview, CodeInput (C-32), ConfirmDialog preview, ConfirmDialog (C-33)

### Community 88 - "Data Display Previews"
Cohesion: 0.50
Nodes (4): DataTable preview, DataTable (C-29), EmptyState preview, EmptyState (C-34)

### Community 89 - "Grant SQL Schema"
Cohesion: 0.83
Nodes (3): sign_in_grant, sign_in_grant_link_token_hash_uq, sign_in_grant_live_by_canonical_email_idx

### Community 91 - "Project Metadata"
Cohesion: 0.50
Nodes (3): name, packageManager, private

### Community 92 - "Smoke Test Script"
Cohesion: 1.00
Nodes (3): fail(), smoke.sh script, wait_for()

### Community 97 - "KPI Dashboard Icons"
Cohesion: 0.67
Nodes (3): Trending Down Icon, Trending Up Icon, KPI Tiles UI Preview (Light)

### Community 98 - "Dashboard Control Components"
Cohesion: 0.67
Nodes (3): StatusBanner (C-04), StopAllButton (C-03), TimeChart (C-39)

### Community 99 - "Feedback UI Components"
Cohesion: 0.67
Nodes (3): Toast (C-36), TtlCountdown (C-13), UndoBar (C-14)

### Community 100 - "Verdict UI Components"
Cohesion: 0.67
Nodes (3): Verdict preview, Verdict component (C-12), Verdict values: Clearly yes, Likely yes, Not sure, Clearly no

### Community 102 - "API Contract Sync"
Cohesion: 1.00
Nodes (3): API sync report (platform-skeleton), OpenAPI contract (platform-skeleton), Data model (platform-skeleton)

## Knowledge Gaps
- **418 isolated node(s):** `TdlibFacade`, `AccountSwitcherProps`, `AiTraceProps`, `AppShellProps`, `AssistantCardProps` (+413 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 646 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **156 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SignInSessions` connect `Session Service Logic` to `Sign-in Integration Tests`, `Passkey Integration Tests`, `Sign-in REST Controller`, `Session Persistence Layer`, `Spring Security Configuration`, `Passkey API Tests`, `Session REST Controller`, `Sign-in Security Tests`, `Session API Tests`, `Passkey Security Config`, `Sign-in Business Logic`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **Why does `SignIn` connect `Sign-in Business Logic` to `Session Service Logic`, `Sign-in REST Controller`, `Sign-in Email Service`, `Sign-in Security Tests`, `Mail Failure Tests`, `Owner Management Service`, `Sign-in Issuance Tests`, `Sign-in Secret Hashing`, `Sign-in Grant Repository`, `Sign-in Notification Tests`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Why does `OwnerId` connect `Owner Management Service` to `Sign-in Integration Tests`, `Passkey Integration Tests`, `Session Service Logic`, `Session Persistence Layer`, `Passkey API Tests`, `Session REST Controller`, `Passkey Management Service`, `Session API Tests`, `Passkey Security Config`, `Typed ID Utilities`?**
  _High betweenness centrality (0.014) - this node is a cross-community bridge._
- **Are the 35 inferred relationships involving `Icon()` (e.g. with `telex/components/bundle.js` and `AccountSwitcher()`) actually correct?**
  _`Icon()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **Are the 35 inferred relationships involving `Icon()` (e.g. with `design-system/components/bundle.js` and `AccountSwitcher()`) actually correct?**
  _`Icon()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **What connects `TdlibFacade`, `AccountSwitcherProps`, `AiTraceProps` to the rest of the system?**
  _418 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Sign-in Integration Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.0635350797952424 - nodes in this community are weakly interconnected._