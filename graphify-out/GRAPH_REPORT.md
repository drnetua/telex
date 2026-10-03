# Graph Report - telex  (2026-10-03)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 2541 nodes · 5382 edges · 289 communities (91 shown, 198 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 402 edges (avg confidence: 0.84)
- Token cost: 9,237 input · 11,017 output

## Graph Freshness
- Built from commit: `24c959fa`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- E2E Testing Configuration
- Sign-in Notice Integration Tests
- Identity Module Tasks
- Auth Navigation Logic
- Telex UI Components
- Design System Components
- Account Domain Models
- Session Event Integration Tests
- UI Component Type Definitions
- Sign-in Frontend Logic
- API Client Infrastructure
- Passkey Security Configuration
- Passkey Credential Records
- Identity Architecture ADRs
- Identity Schema Integration Tests
- Project Architecture Overview
- Application Routing Layouts
- React UI Library
- Preferences API Integration Tests
- Pulse API Integration Tests
- User Preferences State
- Sign-in API Integration Tests
- Project Tracking and Planning
- Session API Integration Tests
- Spring Security Configuration
- Domain Value Object Tests
- Sign-in Issuance Integration Tests
- Owner Management Service
- Sign-in REST Controller
- Passkey API Integration Tests
- Error Handling Tests
- Settings UI Components
- Owner Preferences Integration Tests
- Sign-in Redemption Integration Tests
- Sign-in Grant Repository
- API Error Response Handling
- UI Component Previews
- WebAuthn Client API
- Session Persistence Repository
- Mail Failure Integration Tests
- App Shell Navigation
- Sign-in Domain Service
- WebAuthn Software Authenticator
- Frontend Linting Configuration
- Security Secret Generation
- Typed ID Generation
- Me Endpoint Controller
- Frontend Development Dependencies
- Spring Security Session Repository
- Frontend TypeScript Configuration
- Passkey Persistence Integration Tests
- Security Chain Integration Tests
- Inbox Aggregation Logic
- System Module Architecture
- Session Management Service
- Sessions REST Controller
- Pulse Test Fixtures
- Passkey Registration Filter Tests
- Security Secret Hygiene Tests
- Passkey Management Service
- Device Labeling Logic
- Email Address Validation
- Product Screen Mockups
- AI Agent Workflow
- Sign-in Notification Service
- SPA Hosting Integration Tests
- Session Cookie Management
- Condition Registry Logic
- Chat UI Components
- OpenAPI Contract Validation
- Pulse REST Controller
- Email Delivery Service
- Passkeys REST Controller
- Application Feature Map
- Project Roadmap Phases
- SPA Static Resource Hosting
- E2E TypeScript Configuration
- SMTP Mailer Implementation
- App Shell Documentation
- Frontend Build Scripts
- Passkey Persistence Repository
- Gradle Test Conventions
- AI Agent Decision Logic
- Assistant Creation Workflow
- AI Interaction Scenarios
- Owner Profile Service
- Time Zones REST Controller
- Public URL Normalization Tests
- Application Smoke Tests
- Theme Domain Model
- Spring Boot Application Entry
- SMTP Mailer Error Tests
- Core Product Documentation
- Assistant Editor Components
- App Shell Architecture ADRs
- Session Database Migrations
- Passkey Database Migrations
- Theme and Contract Tasks
- Sign-in Session Schema
- Passkey Authentication Schema
- Frontend Core Setup
- Coolify Deployment Scripts
- Knowledge Graph Updates
- Event Publication Schema
- Sign-in Grant Schema
- Database and Infrastructure
- UI Data Visualizations
- Design System and Accessibility
- Input and Dialog Components
- Data Display Components
- App Shell Design
- App Shell Development Tasks
- Sign-in Grant Migration
- Node Package Configuration
- Smoke Test Scripts
- Owner Table Schema
- Project Deployment Configuration
- KPI and Trend UI
- Status and Control Components
- Notification and Feedback Components
- Verdict Selection Component
- LLM Agent Execution Logic
- Model Profile Specifications
- API and Data Contracts
- Owner Migration Script
- Telegram Integration Specifications
- Vite Build Configuration
- Telegram Library Facade
- AI Reviewer Documentation
- CI and E2E Testing
- Local Email Auth Testing
- Brand Assets
- Agent Execution Model
- Cost Display Component
- Brand Cover Assets
- Feedback Toggle Component
- Scope Picker Component
- Sensitivity Slider Component
- Side Panel Component
- Status Banner Component
- Stop All Button Component
- Test Results Component
- Time Chart Component
- Toast Notification Component
- Trigger Editor Component
- TTL Countdown Component
- Undo Bar Component
- Approval Workflow Logic
- Theme Logic and Documentation
- Theme Validation Tasks
- Platform Skeleton Review
- System Error Pages
- Prettier Code Formatting
- Knowledge Graph Workflow
- Application Configuration
- Detekt Configuration
- Error Fallback Logic
- Linked Account Management
- Model Catalog Management
- Model Profile Details
- Model Slot Configuration
- System Operator Role
- Passkey Authentication
- Status Banner Component
- Activity Status Icon
- Alert Circle Icon
- Alert Triangle Icon
- Back Arrow Icon
- Forward Arrow Icon
- Ban Action Icon
- Notification Bell Icon
- Bolt Action Icon
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
- CPU Hardware Icon
- Currency Dollar Icon
- Vertical Dots Icon
- File Download Icon
- Visibility Eye Icon
- File Document Icon
- Data Filter Icon
- Hand Stop Icon
- Help Circle Icon
- History Log Icon
- Hourglass Timer Icon
- Mail Inbox Icon
- Info Circle Icon
- Dashboard Layout Icon
- Security Lock Icon
- User Logout Icon
- Navigation Menu Icon
- Message Circle Icon
- Chat Messages Icon
- Minus Action Icon
- Attachment Paperclip Icon
- Edit Pencil Icon
- Photo Image Icon
- Player Pause Icon
- Player Play Icon
- Plus Add Icon
- Refresh Action Icon
- Search Query Icon
- List Selector Icon
- Send Message Icon
- System Settings Icon
- Shield Lock Icon
- Sparkles Effect Icon
- Target Goal Icon
- Thumb Down Icon
- Thumb Up Icon
- Trash Delete Icon
- User Profile Icon
- Users Group Icon
- Video Camera Icon
- Wifi Off Icon
- Close X Icon
- Tooling Scope Rules
- App Shell PR
- App Shell Schema
- Documentation Review Task
- Platform Skeleton Epic
- Frontend Entry Point
- PNPM Workspace Config

## God Nodes (most connected - your core abstractions)
1. `react` - 39 edges
2. `Icon()` - 36 edges
3. `Icon()` - 36 edges
4. `PreferencesApiIT` - 34 edges
5. `Owners` - 34 edges
6. `OwnerId` - 34 edges
7. `Button()` - 34 edges
8. `messages` - 34 edges
9. `vitest` - 32 edges
10. `SignInApiIT` - 31 edges

## Surprising Connections (you probably didn't know these)
- `Coolify deploy compose.yaml` --semantically_similar_to--> `Root compose.yaml`  [INFERRED] [semantically similar]
  deploy/coolify/compose.yaml → compose.yaml
- `Flyway migrations with rollback scripts` --shares_data_with--> `Postgres pgvector database`  [INFERRED]
  docs/adr/0003-postgres-jdbc-flyway-uuidv7-persistence.md → compose.yaml
- `Quartz JDBC scheduler` --shares_data_with--> `Postgres pgvector database`  [INFERRED]
  docs/adr/0004-quartz-jdbc-scheduler.md → compose.yaml
- `Telex Logo` --semantically_similar_to--> `Telex Logo`  [EXTRACTED] [semantically similar]
  docs/docs/design-system/assets/Logos/telex-logo.png → frontend/src/assets/telex-logo.png
- `SmtpMailerTest` --calls--> `SmtpMailer`  [INFERRED]
  backend/app/src/test/kotlin/telex/mail/internal/SmtpMailerTest.kt → backend/app/src/main/kotlin/telex/mail/internal/SmtpMailer.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **App Shell Live Signals Flow** — docs_features_app_shell_adr_0002, docs_features_app_shell_adr_0003, docs_features_app_shell_adr_0004, docs_features_app_shell_adr_0006 [EXTRACTED 0.95]
- **Four ACL integration modules** — docs_docs_img_architecture_modules_telegram, docs_docs_img_architecture_modules_llm, docs_docs_img_architecture_modules_decision, docs_docs_img_architecture_modules_bot [EXTRACTED 1.00]
- **AI Model Configuration Hierarchy** — context_model_catalog, context_model_profile, context_model_slot, context_fallback_chain [EXTRACTED 1.00]
- **Work shell navigation sections** — docs_docs_img_app_map_overview, docs_docs_img_app_map_inbox, docs_docs_img_app_map_chats, docs_docs_img_app_map_agents, docs_docs_img_app_map_runs, docs_docs_img_app_map_tasks, docs_docs_img_app_map_settings [EXTRACTED 1.00]
- **App Shell Review Cycle 1 Fixes** — docs_features_app_shell_tasks_t25_review_fix_a5_c4_b6_t25, docs_features_app_shell_tasks_t26_review_fix_b1_b5_e4_t26, docs_features_app_shell_tasks_t27_review_fix_e1_e3_d3_t27 [EXTRACTED 1.00]
- **App Shell Review Cycle 2 Fixes** — docs_features_app_shell_tasks_t28_review2_fix_storage_revert_tests_t28, docs_features_app_shell_tasks_t29_review2_fix_theme_menu_tab_t29, docs_features_app_shell_tasks_t30_review2_fix_contract_codes_t30, docs_features_app_shell_tasks_t31_review2_fix_ac102_e2e_t31, docs_features_app_shell_tasks_t32_review2_fix_docs_t32 [EXTRACTED 1.00]
- **Core modules communicating via Modulith events** — docs_docs_img_architecture_modules_identity, docs_docs_img_architecture_modules_messaging, docs_docs_img_architecture_modules_triage, docs_docs_img_architecture_modules_agents, docs_docs_img_architecture_modules_tools, docs_docs_img_architecture_modules_tasks, docs_docs_img_architecture_modules_scheduling, docs_docs_img_architecture_modules_audit [EXTRACTED 1.00]
- **teleX core spec documents** — docs_docs_01_tech_spec [EXTRACTED 1.00]
- **F2 create-assistant wizard steps** — docs_docs_img_flow_f2_create_assistant_step_template, docs_docs_img_flow_f2_create_assistant_step_what_to_read, docs_docs_img_flow_f2_create_assistant_step_when, docs_docs_img_flow_f2_create_assistant_step_what_to_do, docs_docs_img_flow_f2_create_assistant_step_test, docs_docs_img_flow_f2_create_assistant_step_enable [EXTRACTED 1.00]
- **F3 draft approval flow** — docs_docs_img_flow_f3_draft_approval_counterpart_message, docs_docs_img_flow_f3_draft_approval_triage_assessment, docs_docs_img_flow_f3_draft_approval_draft_and_task, docs_docs_img_flow_f3_draft_approval_owner_notification, docs_docs_img_flow_f3_draft_approval_send_as_owner [EXTRACTED 1.00]
- **Foundation ADRs 0001-0004** — docs_adr_0001_kotlin_spring_modulith_postgres_react_stack, docs_adr_0002_single_app_with_isolated_tdlib_subproject, docs_adr_0003_postgres_jdbc_flyway_uuidv7_persistence, docs_adr_0004_quartz_jdbc_scheduler [EXTRACTED 1.00]
- **Model Profiles Feature Definition** — docs_features_model_profiles_spec_spec, docs_features_model_profiles_ux_flows_ux_flows, docs_features_model_profiles_adr_0001_model_choice_ships_before_agents_adr0001 [EXTRACTED 1.00]
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
- **Project Skeleton Foundation** — github_workflows_ci_yml, backend_app_src_main_resources_application_yaml, docs_roadmap_md [INFERRED 0.85]
- **Sign-in flow screens** — docs_features_platform_skeleton_screens_scr_01_sign_in, docs_features_platform_skeleton_screens_scr_07_check_your_email, docs_features_platform_skeleton_screens_scr_08_confirm_sign_in_link, docs_features_platform_skeleton_screens_scr_09_create_a_passkey [INFERRED 0.85]

## Communities (289 total, 198 thin omitted)

### Community 0 - "E2E Testing Configuration"
Cohesion: 0.06
Nodes (60): devDependencies, @axe-core/playwright, @playwright/test, @types/node, typescript, @types/node, typescript, name (+52 more)

### Community 1 - "Sign-in Notice Integration Tests"
Cohesion: 0.07
Nodes (6): NewSignInNoticeIT, TestCredentialRecords, RecordingMailer, RecordingMailerConfiguration, PasskeyCeremoniesIT, Registered

### Community 2 - "Identity Module Tasks"
Cohesion: 0.07
Nodes (59): T1 Promote identity migrations, T2 Identity domain primitives, T3 Mail integration module, T4 Owner and session core, T5 Issue and preview sign-in grant, T6 Redeem sign-in link and code, T7 New sign-in notice email, T8 Web security and problem codes (+51 more)

### Community 3 - "Auth Navigation Logic"
Cohesion: 0.07
Nodes (34): AUTH_PAGES, isAuthPage(), rememberDestination(), takeRememberedDestination(), App(), FailureBoundary(), down(), json() (+26 more)

### Community 4 - "Telex UI Components"
Cohesion: 0.11
Nodes (52): AccountSwitcher(), AiTrace(), AppShell(), AssistantCard(), AutonomyPicker(), Avatar(), Badge(), Breakdown() (+44 more)

### Community 5 - "Design System Components"
Cohesion: 0.11
Nodes (52): AccountSwitcher(), AiTrace(), AppShell(), AssistantCard(), AutonomyPicker(), Avatar(), Badge(), Breakdown() (+44 more)

### Community 6 - "Account Domain Models"
Cohesion: 0.08
Nodes (36): deleteTolerant(), DeviceType, isBackground(), Me, meKey, Passkey, passkeysKey, sessionsKey (+28 more)

### Community 7 - "Session Event Integration Tests"
Cohesion: 0.10
Nodes (5): FixedClockConfiguration, MutableClock, SignInSessionsIT, ClockConfiguration, ClockConfigurationTest

### Community 8 - "UI Component Type Definitions"
Cohesion: 0.04
Nodes (46): AccountSwitcherProps, AiTraceProps, AppShellProps, AssistantCardProps, AutonomyPickerProps, AvatarProps, BadgeProps, BotMessageProps (+38 more)

### Community 9 - "Sign-in Frontend Logic"
Cohesion: 0.10
Nodes (33): previewSignInLink(), redeemSignInCode(), redeemSignInLink(), requestSignInEmail(), SignInGrant, landAfterSignIn(), Button(), CodeInput() (+25 more)

### Community 10 - "API Client Infrastructure"
Cohesion: 0.09
Nodes (28): ADR-0005, ApiFailure, apiFetch(), ApiOptions, csrfToken(), FailureRoute, isConnectivityStatus(), routeFor() (+20 more)

### Community 11 - "Passkey Security Configuration"
Cohesion: 0.06
Nodes (3): PasskeyConfiguration, passkeyUserDetails(), TelexRelyingPartyOperations

### Community 12 - "Passkey Credential Records"
Cohesion: 0.08
Nodes (6): Builder, absent(), neverUsed(), NeverUsedTolerantJdbcOperations, NeverUsedTolerantRowMapper, PasskeyRepositories

### Community 13 - "Identity Architecture ADRs"
Cohesion: 0.06
Nodes (36): Data-model audit 2026-10-02, Staged migrations: owner, sign_in_grant, sign_in_session, passkey tables, Changelog platform-skeleton, PR body platform-skeleton (E01), ADR-0001 Sign-in sessions in identity-owned table with opaque hashed cookie, ADR-0002 Spring Security WebAuthn for passkeys, ADR-0003 Sign-in link and code as one hashed single-use grant, ADR-0004 New mail integration module (+28 more)

### Community 14 - "Identity Schema Integration Tests"
Cohesion: 0.09
Nodes (3): IdentitySchemaIT, MigrationRollbackIT, TestcontainersConfiguration

### Community 15 - "Project Architecture Overview"
Cohesion: 0.06
Nodes (40): ADR-0001 stack, Kotlin + Spring Boot 4 + Postgres + React stack, ADR-0002 single app + TDLib subproject, Rule: only telegram module imports TDLib, telegram-tdlib subproject, ADR-0003 persistence, ADR-0004 Quartz scheduler, Architecture map (+32 more)

### Community 16 - "Application Routing Layouts"
Cohesion: 0.12
Nodes (25): AppRoutes(), built, comingSoon(), Loader, sectionLoaders, open(), unbuilt, AppLayout() (+17 more)

### Community 17 - "React UI Library"
Cohesion: 0.11
Nodes (24): BadgeProps, ButtonProps, EmptyStateProps, Icon(), IconName, icons, Menu(), options (+16 more)

### Community 19 - "Pulse API Integration Tests"
Cohesion: 0.15
Nodes (3): PulseApiIT, PulseApiSupport, PulseFixtureApiIT

### Community 20 - "User Preferences State"
Cohesion: 0.13
Nodes (30): changePreferences(), chooseTheme(), goesToFailureRouting(), readThemeSave(), SavedPreferences, ThemeSave, themeSaveKey, TimeZoneSaveResult (+22 more)

### Community 22 - "Project Tracking and Planning"
Cohesion: 0.07
Nodes (30): AC-98, MigrationRollbackIT column snapshot includes character_maximum_length, NewSignInNoticeIT asserts exactly 1 incomplete publication, T36 review fix R9 R10 task, platform-skeleton task tracker (T1-T36), Passkey (WebAuthn), Sign-in Grant, Sign-in Session (+22 more)

### Community 25 - "Domain Value Object Tests"
Cohesion: 0.10
Nodes (4): TimeZones, ThemeTest, TimeZonesTest, ModularityTest

### Community 27 - "Owner Management Service"
Cohesion: 0.14
Nodes (3): Owners, OwnerPreferences, Preferences

### Community 28 - "Sign-in REST Controller"
Cohesion: 0.17
Nodes (7): CodeBody, GrantCreated, LinkPreviewed, LinkTokenBody, RequestSignInEmail, SignedInBody, SignInController

### Community 30 - "Error Handling Tests"
Cohesion: 0.12
Nodes (3): ProbeBody, ProbeController, ProblemHandlerTest

### Community 31 - "Settings UI Components"
Cohesion: 0.14
Nodes (14): useListTimeZones(), ChunkBoundary, SectionRoute(), LoadState(), LoadStateProps, label(), matches(), setup() (+6 more)

### Community 34 - "Sign-in Grant Repository"
Cohesion: 0.18
Nodes (5): SignInGrantId, GrantRow, GrantRows, GrantIssued, TypedId

### Community 36 - "UI Component Previews"
Cohesion: 0.12
Nodes (24): Icon preview, Icon component (Icon), InboxCard preview, InboxCard component (C-11), KpiTile preview, KpiTile component (C-38), LoadState preview, LoadState component (C-35) (+16 more)

### Community 37 - "WebAuthn Client API"
Cohesion: 0.16
Nodes (18): cancelledOr(), canCreatePasskey(), createPasskey(), CreationOptionsJson, DescriptorJson, descriptors(), PasskeyCancelled, RequestOptionsJson (+10 more)

### Community 38 - "Session Persistence Repository"
Cohesion: 0.20
Nodes (6): OwnerId, SignInSessionId, ListedRow, SessionRow, SessionRows, SignInSessionStarted

### Community 39 - "Mail Failure Integration Tests"
Cohesion: 0.15
Nodes (3): FailingSender, SignInMailFailureIT, ThrowingSender

### Community 40 - "App Shell Navigation"
Cohesion: 0.19
Nodes (18): useSignOut(), AppShell(), AppShellProps, isPhoneNow(), MoreSheet(), PhoneBar(), SectionLabel(), SideMenu() (+10 more)

### Community 41 - "Sign-in Domain Service"
Cohesion: 0.23
Nodes (6): GrantRefusals, GrantRefused, LinkPreview, SignedIn, SignIn, DomainProblem

### Community 43 - "Frontend Linting Configuration"
Cohesion: 0.12
Nodes (18): @types/node, typescript, name, private, type, version, eslint, @eslint/js (+10 more)

### Community 45 - "Typed ID Generation"
Cohesion: 0.17
Nodes (4): Uuid7, IdsTest, IdsTest, SampleId

### Community 46 - "Me Endpoint Controller"
Cohesion: 0.22
Nodes (6): FieldProblem, DetectedTimeZoneBody, MeBody, MeController, PreferencesBody, toBody()

### Community 47 - "Frontend Development Dependencies"
Cohesion: 0.11
Nodes (19): devDependencies, eslint, @eslint/js, eslint-plugin-react-hooks, eslint-plugin-react-refresh, globals, jsdom, prettier (+11 more)

### Community 49 - "Frontend TypeScript Configuration"
Cohesion: 0.11
Nodes (17): compilerOptions, isolatedModules, jsx, lib, module, moduleResolution, noEmit, noFallthroughCasesInSwitch (+9 more)

### Community 53 - "Inbox Aggregation Logic"
Cohesion: 0.22
Nodes (4): Inbox, InboxSource, FixedSource, InboxTest

### Community 54 - "System Module Architecture"
Cohesion: 0.15
Nodes (17): Architecture modules diagram, agents module, audit module, bot module, Core layer (Modulith events), decision module, identity module, Integrations layer (anti-corruption) (+9 more)

### Community 55 - "Session Management Service"
Cohesion: 0.22
Nodes (7): Ended, Live, MySession, SessionResolution, SignInSessions, StartedSession, Unknown

### Community 56 - "Sessions REST Controller"
Cohesion: 0.23
Nodes (4): SignedInOwner, SessionItem, SessionList, SessionsController

### Community 63 - "Product Screen Mockups"
Cohesion: 0.21
Nodes (14): SCR-30 Assistants, SCR-31/32 Assistant builder with test, SCR-21/22 Chat with Why panel, SCR-80 Overview dark, SCR-80 Overview first day, SCR-10 Inbox, teleX screens preview index, SCR-80 Overview desktop light (+6 more)

### Community 64 - "AI Agent Workflow"
Cohesion: 0.14
Nodes (13): Agent run: gathers context and decides what to do, Stop: AI does not see this, Output by autonomy level (note, draft, action), Blocked -> flag + Inbox, Under the hood: every message passes three filters before the assistant acts, Doubt -> Review inbox, Drafts -> Inbox for approval, Journal and explanations (what it saw, why, what it did, cost) (+5 more)

### Community 65 - "Sign-in Notification Service"
Cohesion: 0.26
Nodes (3): NewSignInNotice, Notice, Mailer

### Community 69 - "Condition Registry Logic"
Cohesion: 0.21
Nodes (10): byCode, catalog, Condition, ConditionAction, Entry, logged, orderConditions(), resetUnknownConditions() (+2 more)

### Community 70 - "Chat UI Components"
Cohesion: 0.18
Nodes (11): AI trace (C-08), ChatPicker preview, ChatPicker (C-30), ChatRow preview, ChatRow (C-06), Chip preview, Chip, Composer preview (+3 more)

### Community 72 - "Pulse REST Controller"
Cohesion: 0.29
Nodes (3): StatusConditionSource, Pulse, PulseController

### Community 73 - "Email Delivery Service"
Cohesion: 0.29
Nodes (3): SignInEmail, MailUnavailable, OutgoingEmail

### Community 74 - "Passkeys REST Controller"
Cohesion: 0.29
Nodes (4): PasskeyItem, PasskeyList, PasskeysController, toItem()

### Community 75 - "Application Feature Map"
Cohesion: 0.22
Nodes (11): Admin console (separate access): SCR-70..72, Agents section: SCR-30..36 list, builder, test, YAML, models, restore, baselines, App map diagram (login in Overview, daily work in Inbox), Chats section: SCR-20..25 list, chat, why, search, media, forward, Inbox section (highlighted): SCR-10 confirm, review; 11 draft edit; today, Login and onboarding (one-time): SCR-01..06, Overview section: SCR-80 KPI, activity, spend, reliability, Runs section: SCR-40 run feed, SCR-41 run details (+3 more)

### Community 76 - "Project Roadmap Phases"
Cohesion: 0.25
Nodes (11): Roadmap diagram: biweekly gates with demoable results, G0 gate: skeleton exists, CI green, G1 gate: Telegram login and chats in web, G2 gate: agent triggers by meaning, not keyword, G3 MVP gate: 3 demo scenarios work live, G4 gate: demo day, course retro, Phase Automation (weeks 5-6): E15, E17-E19 images, bot, approval; E20-E23 digest, chains, tasks, stop; E24-E26 protection, journal, admin, Phase Brain (weeks 3-4): E07-E10 search, scope, agents, models; E11-E14, E16 triggers, runtime, test; parallel worktree lanes (+3 more)

### Community 78 - "E2E TypeScript Configuration"
Cohesion: 0.20
Nodes (9): compilerOptions, module, moduleResolution, noEmit, skipLibCheck, strict, target, types (+1 more)

### Community 80 - "App Shell Documentation"
Cohesion: 0.22
Nodes (9): Changelog — app-shell, ADR-0001: Shell Scope Moves, ADR-0002: Background Pulse Polling, ADR-0003: Inbox Module Aggregation, ADR-0004: Offline Detection, ADR-0005: Preference Storage, ADR-0006: Shell Extension Registries, OpenAPI Contract — app-shell (+1 more)

### Community 81 - "Frontend Build Scripts"
Cohesion: 0.22
Nodes (9): scripts, build, check, dev, format, lint, preview, test (+1 more)

### Community 83 - "Gradle Test Conventions"
Cohesion: 0.25
Nodes (7): dependencies, react, react-dom, react-router, @tabler/core, @tabler/icons-react, @tanstack/react-query

### Community 84 - "AI Agent Decision Logic"
Cohesion: 0.25
Nodes (7): Action (reply, draft, task, approval if risky), Guardrail (JevGuardrailAdvisor, self-refine via JevJudge), Jev triage (Noul/Choice/Score, value + confidence), LLM agent (OpenRouter, agent model, tools, memory), Prefilter (agent channel scope, keywords, regex), Schedule / manual task (bypasses System 1), TDLib event (updateNewMessage normalized)

### Community 85 - "Assistant Creation Workflow"
Cohesion: 0.32
Nodes (8): Flow F2: create assistant (enable only after test on real messages), Decision: Does it fit?, Step 6: Enable (works from now, autonomy Suggest), Step 1: Template (Digest, Guard, Secretary, Custom), Step 5: Test on last week's messages (hit rate and cost), Step 4: What to do (actions + autonomy, model and budget), Step 2: What to read (chat sets, private is gray), Step 3: When (words, sense, schedule, command)

### Community 86 - "AI Interaction Scenarios"
Cohesion: 0.29
Nodes (8): Counterpart writes in chat, Counterpart receives ordinary reply, Draft reply plus task, Owner notification in Saved Messages with 3 buttons, Reject or TTL: nothing sent, draft archived, lesson for condition, System sends on behalf of Owner, Assessment: confident yes within a moment, Web edit screen SCR-10

### Community 92 - "Theme Domain Model"
Cohesion: 0.33
Nodes (4): Theme, DARK, LIGHT, SYSTEM

### Community 95 - "Core Product Documentation"
Cohesion: 0.33
Nodes (6): Inbox, Owner, Design System Canon, Epics Definition, Product Specification, Project Roadmap

### Community 96 - "Assistant Editor Components"
Cohesion: 0.33
Nodes (6): ScheduleEditor (C-21), ScopePicker (C-18), SensitivitySlider (C-20), SidePanel (C-05), TestResults (C-26), TriggerEditor (C-19)

### Community 97 - "App Shell Architecture ADRs"
Cohesion: 0.40
Nodes (5): ADR-0002: Poll one background pulse every 3 seconds, ADR-0003: Aggregate the Inbox count from sources, ADR-0004: Detect offline from pulse failures, ADR-0006: Extend the shell through registries, Software Architecture Document — app-shell

### Community 98 - "Session Database Migrations"
Cohesion: 0.60
Nodes (3): sign_in_session, sign_in_session_key_hash_uq, sign_in_session_owner_id_idx

### Community 99 - "Passkey Database Migrations"
Cohesion: 0.70
Nodes (4): user_credentials, user_credentials_user_entity_user_id_idx, user_entities, user_entities_name_uq

### Community 100 - "Theme and Contract Tasks"
Cohesion: 0.40
Nodes (5): Task T28: Storage guards and theme revert logic, Task T29: Theme menu accessibility fixes, Task T30: Contract field codes and fixture caps, Task T31: e2e timeout adjustments for AC-102, Task T32: Documentation alignment after review 2

### Community 101 - "Sign-in Session Schema"
Cohesion: 0.60
Nodes (3): sign_in_session, sign_in_session_key_hash_uq, sign_in_session_owner_id_idx

### Community 102 - "Passkey Authentication Schema"
Cohesion: 0.70
Nodes (4): user_credentials, user_credentials_user_entity_user_id_idx, user_entities, user_entities_name_uq

### Community 103 - "Frontend Core Setup"
Cohesion: 0.40
Nodes (3): root, react-dom, @tabler/core

### Community 104 - "Coolify Deployment Scripts"
Cohesion: 0.80
Nodes (4): api(), fail(), deploy-coolify.sh script, step()

### Community 105 - "Knowledge Graph Updates"
Cohesion: 0.50
Nodes (4): fail(), GEMINI_BASE_URL, GRAPHIFY_GEMINI_MODEL, graph-update.sh script

### Community 107 - "Event Publication Schema"
Cohesion: 0.83
Nodes (3): event_publication, event_publication_by_completion_date_idx, event_publication_serialized_event_hash_idx

### Community 108 - "Sign-in Grant Schema"
Cohesion: 0.83
Nodes (3): sign_in_grant, sign_in_grant_link_token_hash_uq, sign_in_grant_live_by_canonical_email_idx

### Community 109 - "Database and Infrastructure"
Cohesion: 0.67
Nodes (4): Postgres pgvector database, Flyway migrations with rollback scripts, UUIDv7 app-generated ids, Quartz JDBC scheduler

### Community 110 - "UI Data Visualizations"
Cohesion: 0.50
Nodes (4): Table Icon, Spend Breakdown UI Preview (Light), Time Chart UI Preview (Dark), Time Chart UI Preview (Light)

### Community 111 - "Design System and Accessibility"
Cohesion: 0.67
Nodes (4): SCR-80 Overview screen, WCAG 2.2 AA accessibility, Tabler 1.6 UI kit, Design tokens, ai purple only for AI content

### Community 112 - "Input and Dialog Components"
Cohesion: 0.50
Nodes (4): CodeInput preview, CodeInput (C-32), ConfirmDialog preview, ConfirmDialog (C-33)

### Community 113 - "Data Display Components"
Cohesion: 0.50
Nodes (4): DataTable preview, DataTable (C-29), EmptyState preview, EmptyState (C-34)

### Community 114 - "App Shell Design"
Cohesion: 0.50
Nodes (4): Screens — app-shell, Spec — app-shell, Test plan — app-shell, UX flows — app-shell

### Community 115 - "App Shell Development Tasks"
Cohesion: 0.50
Nodes (4): Task T25: Build Time zone card states and focus fixes, Task T26: e2e tightening and preference proofs, Task T27: Documentation alignment and ADR updates, App Shell Task Tracker

### Community 116 - "Sign-in Grant Migration"
Cohesion: 0.83
Nodes (3): sign_in_grant, sign_in_grant_link_token_hash_uq, sign_in_grant_live_by_canonical_email_idx

### Community 118 - "Node Package Configuration"
Cohesion: 0.50
Nodes (3): name, packageManager, private

### Community 119 - "Smoke Test Scripts"
Cohesion: 1.00
Nodes (3): fail(), smoke.sh script, wait_for()

### Community 121 - "Project Deployment Configuration"
Cohesion: 0.67
Nodes (3): Root compose.yaml, Coolify deploy compose.yaml, teleX README

### Community 122 - "KPI and Trend UI"
Cohesion: 0.67
Nodes (3): Trending Down Icon, Trending Up Icon, KPI Tiles UI Preview (Light)

### Community 123 - "Status and Control Components"
Cohesion: 0.67
Nodes (3): StatusBanner (C-04), StopAllButton (C-03), TimeChart (C-39)

### Community 124 - "Notification and Feedback Components"
Cohesion: 0.67
Nodes (3): Toast (C-36), TtlCountdown (C-13), UndoBar (C-14)

### Community 125 - "Verdict Selection Component"
Cohesion: 0.67
Nodes (3): Verdict preview, Verdict component (C-12), Verdict values: Clearly yes, Likely yes, Not sure, Clearly no

### Community 127 - "Model Profile Specifications"
Cohesion: 0.67
Nodes (3): ADR 0001: Model choice ships before agents, Spec: Model Profiles, UX Flows: Model Profiles

### Community 128 - "API and Data Contracts"
Cohesion: 1.00
Nodes (3): API sync report (platform-skeleton), OpenAPI contract (platform-skeleton), Data model (platform-skeleton)

### Community 130 - "Telegram Integration Specifications"
Cohesion: 0.67
Nodes (3): ADR 0001: Move agent pause on unlink to agent builder, Spec: Telegram Link, UX Flows: Telegram Link

## Knowledge Gaps
- **492 isolated node(s):** `Mail`, `ApiOptions`, `FailureRoute`, `Listener`, `ConnectivityState` (+487 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 770 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **198 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Owners` connect `Owner Management Service` to `Owner Preferences Integration Tests`, `Sign-in Notice Integration Tests`, `Session Event Integration Tests`, `Sign-in Domain Service`, `Preferences API Integration Tests`, `Passkey Persistence Integration Tests`, `Pulse API Integration Tests`, `Owner Profile Service`, `Session API Integration Tests`, `Passkey Management Service`, `Passkey API Integration Tests`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **Why does `SignInSessions` connect `Session Management Service` to `Sign-in Redemption Integration Tests`, `Sign-in Notice Integration Tests`, `Session Persistence Repository`, `Session Event Integration Tests`, `Sign-in Domain Service`, `Passkey Security Configuration`, `Spring Security Session Repository`, `Session API Integration Tests`, `Sessions REST Controller`, `Sign-in REST Controller`, `Passkey API Integration Tests`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Why does `OwnerId` connect `Session Persistence Repository` to `Sign-in Notice Integration Tests`, `Sign-in Grant Repository`, `Session Event Integration Tests`, `Passkey Security Configuration`, `Typed ID Generation`, `Passkey Persistence Integration Tests`, `Session Management Service`, `Session API Integration Tests`, `Sessions REST Controller`, `Passkey Management Service`, `Passkey API Integration Tests`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **Are the 35 inferred relationships involving `Icon()` (e.g. with `telex/components/bundle.js` and `AccountSwitcher()`) actually correct?**
  _`Icon()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **Are the 35 inferred relationships involving `Icon()` (e.g. with `design-system/components/bundle.js` and `AccountSwitcher()`) actually correct?**
  _`Icon()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Mail`, `ApiOptions`, `FailureRoute` to the rest of the system?**
  _492 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `E2E Testing Configuration` be split into smaller, more focused modules?**
  _Cohesion score 0.05906553041434029 - nodes in this community are weakly interconnected._