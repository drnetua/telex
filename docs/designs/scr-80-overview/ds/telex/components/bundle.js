/* @ds-bundle: {"format":4,"namespace":"TeleX","components":[{"name":"Icon"},{"name":"Button"},{"name":"Badge"},{"name":"Avatar"},{"name":"Chip"},{"name":"AppShell"},{"name":"AccountSwitcher"},{"name":"StopAllButton"},{"name":"StatusBanner"},{"name":"SidePanel"},{"name":"ChatRow"},{"name":"Message"},{"name":"AiTrace"},{"name":"Composer"},{"name":"MediaThumb"},{"name":"InboxCard"},{"name":"Verdict"},{"name":"TtlCountdown"},{"name":"UndoBar"},{"name":"FeedbackToggle"},{"name":"AssistantCard"},{"name":"AutonomyPicker"},{"name":"ScopePicker"},{"name":"TriggerEditor"},{"name":"SensitivitySlider"},{"name":"ScheduleEditor"},{"name":"ModelProfilePicker"},{"name":"BudgetField"},{"name":"RunStatus"},{"name":"RunTimeline"},{"name":"TestResults"},{"name":"Cost"},{"name":"FilterBar"},{"name":"DataTable"},{"name":"ChatPicker"},{"name":"OnboardingSteps"},{"name":"CodeInput"},{"name":"ConfirmDialog"},{"name":"EmptyState"},{"name":"LoadState"},{"name":"Toast"},{"name":"BotMessage"},{"name":"KpiTile"},{"name":"TimeChart"},{"name":"Breakdown"},{"name":"PeriodPicker"}]} */
(function () {
  var React = window.React;
  var h = React.createElement, useState = React.useState, useEffect = React.useEffect, Fragment = React.Fragment;
  var ICONS = {"inbox":["M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2l0 -12","M4 13h3l3 3h4l3 -3h3"],"message-circle":["M3 20l1.3 -3.9c-2.324 -3.437 -1.426 -7.872 2.1 -10.374c3.526 -2.501 8.59 -2.296 11.845 .48c3.255 2.777 3.695 7.266 1.029 10.501c-2.666 3.235 -7.615 4.215 -11.574 2.293l-4.7 1"],"messages":["M21 14l-3 -3h-7a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1h9a1 1 0 0 1 1 1v10","M14 15v2a1 1 0 0 1 -1 1h-7l-3 3v-10a1 1 0 0 1 1 -1h2"],"sparkles":["M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2m0 -12a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2m-7 12a6 6 0 0 1 6 -6a6 6 0 0 1 -6 -6a6 6 0 0 1 -6 6a6 6 0 0 1 6 6"],"activity":["M3 12h4l3 8l4 -16l3 8h4"],"checklist":["M9.615 20h-2.615a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8","M14 19l2 2l4 -4","M9 8h4","M9 12h2"],"settings":["M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065","M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0"],"shield-lock":["M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3","M11 11a1 1 0 1 0 2 0a1 1 0 1 0 -2 0","M12 12l0 2.5"],"lock":["M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6","M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0","M8 11v-4a4 4 0 1 1 8 0v4"],"hand-stop":["M8 13v-7.5a1.5 1.5 0 0 1 3 0v6.5","M11 5.5v-2a1.5 1.5 0 1 1 3 0v8.5","M14 5.5a1.5 1.5 0 0 1 3 0v6.5","M17 7.5a1.5 1.5 0 0 1 3 0v8.5a6 6 0 0 1 -6 6h-2h.208a6 6 0 0 1 -5.012 -2.7a69.74 69.74 0 0 1 -.196 -.3c-.312 -.479 -1.407 -2.388 -3.286 -5.728a1.5 1.5 0 0 1 .536 -2.022a1.867 1.867 0 0 1 2.28 .28l1.47 1.47"],"player-pause":["M6 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -12","M14 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -12"],"player-play":["M7 4v16l13 -8l-13 -8"],"refresh":["M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4","M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4"],"search":["M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0","M21 21l-6 -6"],"filter":["M4 4h16v2.172a2 2 0 0 1 -.586 1.414l-4.414 4.414v7l-6 2v-8.5l-4.48 -4.928a2 2 0 0 1 -.52 -1.345v-2.227"],"send":["M10 14l11 -11","M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5"],"arrow-back-up":["M9 14l-4 -4l4 -4","M5 10h11a4 4 0 1 1 0 8h-1"],"check":["M5 12l5 5l10 -10"],"x":["M18 6l-12 12","M6 6l12 12"],"pencil":["M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4","M13.5 6.5l4 4"],"eye":["M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0","M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6"],"bulb":["M3 12h1m8 -9v1m8 8h1m-15.4 -6.4l.7 .7m12.1 -.7l-.7 .7","M9 16a5 5 0 1 1 6 0a3.5 3.5 0 0 0 -1 3a2 2 0 0 1 -4 0a3.5 3.5 0 0 0 -1 -3","M9.7 17l4.6 0"],"bolt":["M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11"],"clock":["M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0","M12 7v5l3 3"],"hourglass":["M6.5 7h11","M6.5 17h11","M6 20v-2a6 6 0 1 1 12 0v2a1 1 0 0 1 -1 1h-10a1 1 0 0 1 -1 -1","M6 4v2a6 6 0 1 0 12 0v-2a1 1 0 0 0 -1 -1h-10a1 1 0 0 0 -1 1"],"alert-triangle":["M12 9v4","M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0","M12 16h.01"],"alert-circle":["M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0","M12 8v4","M12 16h.01"],"info-circle":["M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0","M12 9h.01","M11 12h1v4h1"],"ban":["M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0","M5.7 5.7l12.6 12.6"],"photo":["M15 8h.01","M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12","M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5","M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3"],"file":["M14 3v4a1 1 0 0 0 1 1h4","M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2"],"video":["M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4","M3 8a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2l0 -8"],"paperclip":["M15 7l-6.5 6.5a1.5 1.5 0 0 0 3 3l6.5 -6.5a3 3 0 0 0 -6 -6l-6.5 6.5a4.5 4.5 0 0 0 9 9l6.5 -6.5"],"arrow-forward-up":["M15 14l4 -4l-4 -4","M19 10h-11a4 4 0 1 0 0 8h1"],"help-circle":["M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0","M12 16v.01","M12 13a2 2 0 0 0 .914 -3.782a1.98 1.98 0 0 0 -2.414 .483"],"currency-dollar":["M16.7 8a3 3 0 0 0 -2.7 -2h-4a3 3 0 0 0 0 6h4a3 3 0 0 1 0 6h-4a3 3 0 0 1 -2.7 -2","M12 3v3m0 12v3"],"calendar":["M4 7a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12","M16 3v4","M8 3v4","M4 11h16","M11 15h1","M12 15v3"],"bell":["M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6","M9 17v1a3 3 0 0 0 6 0v-1"],"user":["M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0","M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2"],"users":["M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0","M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2","M16 3.13a4 4 0 0 1 0 7.75","M21 21v-2a4 4 0 0 0 -3 -3.85"],"logout":["M14 8v-2a2 2 0 0 0 -2 -2h-7a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h7a2 2 0 0 0 2 -2v-2","M9 12h12l-3 -3","M18 15l3 -3"],"brand-telegram":["M15 10l-4 4l6 6l4 -16l-18 7l4 2l2 6l3 -4"],"plus":["M12 5l0 14","M5 12l14 0"],"trash":["M4 7l16 0","M10 11l0 6","M14 11l0 6","M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12","M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3"],"dots-vertical":["M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0","M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0","M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0"],"menu-2":["M4 6l16 0","M4 12l16 0","M4 18l16 0"],"chevron-down":["M6 9l6 6l6 -6"],"chevron-right":["M9 6l6 6l-6 6"],"download":["M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2","M7 11l5 5l5 -5","M12 4l0 12"],"code":["M7 8l-4 4l4 4","M17 8l4 4l-4 4","M14 4l-4 16"],"cpu":["M5 6a1 1 0 0 1 1 -1h12a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-12a1 1 0 0 1 -1 -1l0 -12","M9 9h6v6h-6l0 -6","M3 10h2","M3 14h2","M10 3v2","M14 3v2","M21 10h-2","M21 14h-2","M14 21v-2","M10 21v-2"],"target":["M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0","M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0","M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"],"thumb-up":["M7 11v8a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1h3a4 4 0 0 0 4 -4v-1a2 2 0 0 1 4 0v5h3a2 2 0 0 1 2 2l-1 5a2 3 0 0 1 -2 2h-7a3 3 0 0 1 -3 -3"],"thumb-down":["M7 13v-8a1 1 0 0 0 -1 -1h-2a1 1 0 0 0 -1 1v7a1 1 0 0 0 1 1h3a4 4 0 0 1 4 4v1a2 2 0 0 0 4 0v-5h3a2 2 0 0 0 2 -2l-1 -5a2 3 0 0 0 -2 -2h-7a3 3 0 0 0 -3 3"],"history":["M12 8l0 4l2 2","M3.05 11a9 9 0 1 1 .5 4m-.5 5v-5h5"],"circle-check":["M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0","M9 12l2 2l4 -4"],"wifi-off":["M12 18l.01 0","M9.172 15.172a4 4 0 0 1 5.656 0","M6.343 12.343a7.963 7.963 0 0 1 3.864 -2.14m4.163 .155a7.965 7.965 0 0 1 3.287 2","M3.515 9.515a12 12 0 0 1 3.544 -2.455m3.101 -.92a12 12 0 0 1 10.325 3.374","M3 3l18 18"],"selector":["M8 9l4 -4l4 4","M16 15l-4 4l-4 -4"],"layout-dashboard":["M5 4h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1","M5 16h4a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1","M15 12h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1","M15 4h4a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1"],"trending-up":["M3 17l6 -6l4 4l8 -8","M14 7l7 0l0 7"],"trending-down":["M3 7l6 6l4 -4l8 8","M21 10l0 7l-7 0"],"table":["M3 5a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14","M3 10h18","M10 3v18"],"chart-bar":["M3 13a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -6","M15 9a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -10","M9 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -14","M4 20h14"],"minus":["M5 12l14 0"]};
  function cx() { return Array.prototype.filter.call(arguments, Boolean).join(' '); }

  /* ---------- Primitives (Tabler) ---------- */
  function Icon(p) {
    var d = ICONS[p.name] || [];
    var s = p.size || 20;
    return h('svg', { className: cx('tx-icon', p.className), width: s, height: s, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: p.stroke || 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': p.label ? undefined : true, role: p.label ? 'img' : undefined, 'aria-label': p.label },
      d.map(function (x, i) { return h('path', { key: i, d: x }); }));
  }
  function Button(p) {
    var v = p.variant || 'secondary', size = p.size || 'md';
    return h('button', { type: p.type || 'button', className: cx('tx-btn', 'tx-btn-' + v, size === 'sm' && 'tx-btn-sm', p.block && 'tx-btn-block', p.className), disabled: p.disabled, onClick: p.onClick, 'aria-label': p.ariaLabel, title: p.title },
      p.icon && h(Icon, { name: p.icon, size: size === 'sm' ? 16 : 18 }), p.children,
      p.kbd && h('kbd', { className: 'tx-kbd' }, p.kbd));
  }
  function Badge(p) {
    return h('span', { className: cx('tx-badge', 'tx-badge-' + (p.tone || 'neutral'), p.className) }, p.icon && h(Icon, { name: p.icon, size: 14 }), p.children);
  }
  function Avatar(p) {
    var initials = (p.name || '?').split(' ').map(function (w) { return w[0]; }).slice(0, 2).join('').toUpperCase();
    return h('span', { className: cx('tx-avatar', p.size === 'sm' && 'tx-avatar-sm'), 'aria-hidden': true, style: p.hue != null ? { background: 'hsl(' + p.hue + ' 45% 88%)', color: 'hsl(' + p.hue + ' 45% 28%)' } : null },
      p.icon ? h(Icon, { name: p.icon, size: 18 }) : initials);
  }

  /* ---------- Frame ---------- */
  var NAV = [['overview', 'Overview', 'layout-dashboard'], ['inbox', 'Inbox', 'inbox'], ['chats', 'Chats', 'messages'], ['assistants', 'Assistants', 'sparkles'], ['runs', 'Runs', 'activity'], ['tasks', 'Tasks', 'checklist'], ['settings', 'Settings', 'settings']];
  function AppShell(p) {
    var active = p.active || 'inbox', mobile = p.layout === 'phone';
    var nav = NAV.map(function (n) {
      return h('a', { key: n[0], href: '#' + n[0], className: cx('tx-nav-item', n[0] === active && 'is-active'), 'aria-current': n[0] === active ? 'page' : undefined },
        h(Icon, { name: n[2] }), h('span', null, n[1]), n[0] === 'inbox' && p.inboxCount ? h('span', { className: 'tx-count', 'aria-label': p.inboxCount + ' items need you' }, p.inboxCount) : null);
    });
    var header = h('header', { className: 'tx-header' },
      mobile ? h('strong', { className: 'tx-header-title' }, p.title || 'Inbox') : h('div', { className: 'tx-header-left' }, p.accounts || null),
      h('div', { className: 'tx-header-right' }, h(StopAllButton, { stopped: p.stopped })));
    return h('div', { className: cx('tx-shell', mobile && 'is-phone') },
      !mobile && h('aside', { className: 'tx-sidebar' }, h('div', { className: 'tx-brand' }, h('span', { className: 'tx-brand-mark' }, 'tX'), 'teleX'), h('nav', { 'aria-label': 'Main' }, nav)),
      h('div', { className: 'tx-main' }, header, p.banner || null, h('main', { className: 'tx-content' }, p.children)),
      mobile && h('nav', { className: 'tx-bottomnav', 'aria-label': 'Main' }, nav.slice(0, 5)));
  }
  function AccountSwitcher(p) {
    var accounts = p.accounts || [];
    if (accounts.length < 2) return null;
    var cur = p.value || 'all';
    return h('label', { className: 'tx-select' }, h('span', { className: 'tx-visually-hidden' }, 'Account'),
      h('select', { value: cur, onChange: function (e) { p.onChange && p.onChange(e.target.value); } },
        h('option', { value: 'all' }, 'All accounts'), accounts.map(function (a) { return h('option', { key: a.id, value: a.id }, a.name); })),
      h(Icon, { name: 'selector', size: 16 }));
  }
  function StopAllButton(p) {
    return p.stopped
      ? h(Button, { variant: 'primary', size: 'sm', icon: 'player-play', onClick: p.onResume }, 'Resume…')
      : h(Button, { variant: 'danger-outline', size: 'sm', icon: 'hand-stop', onClick: p.onStop, title: 'Stops every assistant within 2 seconds' }, 'Stop all');
  }
  var BANNERS = {
    paused: ['warning', 'player-pause', 'All assistants are paused.', 'Resume'],
    budget: ['danger', 'currency-dollar', 'Monthly budget reached. Assistants stopped until you raise it.', 'Raise budget'],
    account: ['danger', 'wifi-off', 'Work account is disconnected from Telegram.', 'Reconnect'],
    bot: ['danger', 'ban', 'You blocked the teleX bot, so approvals only arrive here.', 'Unblock bot'],
    consent: ['warning', 'alert-triangle', 'AI processing needs your consent before assistants can run.', 'Review consent'],
    triage: ['info', 'clock', 'Triage is delayed. New messages are queued and will be checked shortly.', null]
  };
  function StatusBanner(p) {
    var kinds = p.kinds || [p.kind || 'paused'];
    var b = BANNERS[kinds[0]] || BANNERS.paused;
    return h('div', { className: 'tx-banner tx-banner-' + b[0], role: 'status' },
      h(Icon, { name: b[1] }), h('span', { className: 'tx-banner-text' }, p.text || b[2]),
      kinds.length > 1 && h('button', { className: 'tx-link' }, '+' + (kinds.length - 1) + ' more'),
      b[3] && h(Button, { size: 'sm', variant: 'secondary' }, p.action || b[3]));
  }
  function SidePanel(p) {
    return h('section', { className: cx('tx-panel', p.layout === 'phone' && 'is-sheet'), role: 'dialog', 'aria-label': p.title },
      h('header', { className: 'tx-panel-head' }, h('h3', { className: 'h3' }, p.title),
        h('button', { className: 'tx-iconbtn', 'aria-label': 'Close panel', onClick: p.onClose }, h(Icon, { name: 'x' }))),
      h('div', { className: 'tx-panel-body' }, p.children));
  }

  /* ---------- Chats ---------- */
  function ChatRow(p) {
    return h('div', { className: cx('tx-chatrow', p.selected && 'is-selected', p.disabled && 'is-disabled'), 'aria-disabled': p.disabled || undefined },
      h(Avatar, { name: p.name, hue: p.hue }),
      h('div', { className: 'tx-chatrow-main' },
        h('div', { className: 'tx-chatrow-top' },
          h('span', { className: 'tx-chatrow-name h4' }, p.private && h(Icon, { name: 'lock', size: 14, label: 'Private chat' }), p.name),
          h('span', { className: 'small tx-muted' }, p.time)),
        h('div', { className: 'tx-chatrow-bottom' },
          h('span', { className: 'tx-chatrow-preview' }, p.disabled ? p.disabledReason || 'Private chats can’t be added' : p.preview),
          p.account && h(Badge, null, p.account),
          !p.private && p.trace ? h(AiTrace, { count: p.trace }) : null,
          p.unread ? h('span', { className: 'tx-count' }, p.unread) : null)));
  }
  function Message(p) {
    var k = p.kind || 'text';
    if (k === 'deleted') return h('div', { className: cx('tx-msg', p.out && 'is-out', 'is-deleted') }, h('em', null, 'Message deleted'));
    return h('div', { className: cx('tx-msg', p.out && 'is-out', p.byAssistant && 'is-ai', k === 'blocked' && 'is-blocked', p.compact && 'is-compact') },
      p.author && !p.out && h('div', { className: 'tx-msg-author small' }, p.author),
      p.forwarded && h('div', { className: 'tx-msg-fwd small' }, h(Icon, { name: 'arrow-forward-up', size: 14 }), 'Forwarded from ' + p.forwarded),
      p.replyTo && h('blockquote', { className: 'tx-msg-reply small' }, p.replyTo),
      p.media && h(MediaThumb, { kind: p.media }),
      h('div', { className: 'tx-msg-text' }, p.text),
      h('div', { className: 'tx-msg-meta small' },
        k === 'blocked' && h('span', { className: 'tx-danger' }, h(Icon, { name: 'ban', size: 14 }), 'Blocked by safety check'),
        p.byAssistant && h('span', { className: 'tx-ai-text' }, h(Icon, { name: 'pencil', size: 14 }), 'Written by ' + p.byAssistant),
        p.trace ? h(AiTrace, { count: p.trace }) : null,
        p.edited && h('span', null, 'edited'),
        h('span', null, p.time)));
  }
  function AiTrace(p) {
    if (p.state === 'deferred') return h('button', { className: 'tx-trace is-deferred', title: 'Triage delayed' }, h(Icon, { name: 'clock', size: 14 }), 'later');
    if (!p.count) return null;
    return h('button', { className: 'tx-trace', 'aria-label': p.count + ' assistants looked at this. Why?', onClick: p.onClick }, h(Icon, { name: 'sparkles', size: 14 }), p.count);
  }
  function Composer(p) {
    var st = p.state || 'empty';
    if (st === 'readonly') return h('div', { className: 'tx-composer is-readonly small' }, h(Icon, { name: 'lock', size: 16 }), 'You can’t write in this chat.');
    return h('div', { className: 'tx-composer-wrap' },
      p.replyTo && h('div', { className: 'tx-composer-reply small' }, h(Icon, { name: 'arrow-back-up', size: 14 }), 'Replying to ' + p.replyTo, h('button', { className: 'tx-iconbtn', 'aria-label': 'Cancel reply' }, h(Icon, { name: 'x', size: 14 }))),
      st === 'error' && h('div', { className: 'tx-composer-error small', role: 'alert' }, h(Icon, { name: 'alert-circle', size: 14 }), 'Couldn’t send. Retrying in ' + (p.retryIn || 12) + ' s.', h('button', { className: 'tx-link' }, 'Retry now')),
      h('div', { className: 'tx-composer' },
        h('button', { className: 'tx-iconbtn', 'aria-label': 'Attach file' }, h(Icon, { name: 'paperclip' })),
        h('textarea', { className: 'tx-composer-input', rows: 1, placeholder: 'Write a message', defaultValue: p.value || '', 'aria-label': 'Message' }),
        h(Button, { variant: 'primary', icon: st === 'sending' ? 'hourglass' : 'send', ariaLabel: 'Send', disabled: st === 'sending' })));
  }
  var MEDIA = { photo: ['photo', 'Photo'], video: ['video', 'Video · 0:42'], file: ['file', 'report-q3.pdf · 1.2 MB'], generated: ['sparkles', 'Generated image'], unavailable: ['ban', 'Media unavailable'] };
  function MediaThumb(p) {
    var m = MEDIA[p.kind || 'photo'];
    return h('div', { className: cx('tx-media', 'tx-media-' + (p.kind || 'photo')) }, h(Icon, { name: m[0], size: 24 }), h('span', { className: 'small' }, p.label || m[1]));
  }

  /* ---------- Decisions ---------- */
  var KIND = { draft: ['Draft', 'ai'], doubt: ['Needs a check', 'warning'], blocked: ['Blocked', 'danger'], note: ['Note', 'neutral'], task: ['Due today', 'info'] };
  function InboxCard(p) {
    var k = p.kind || 'draft', meta = KIND[k];
    return h('article', { className: cx('tx-card tx-inbox', 'tx-inbox-' + k, p.focused && 'is-focused'), tabIndex: 0 },
      h('header', { className: 'tx-inbox-head' },
        h(Badge, { tone: meta[1], icon: k === 'draft' ? 'sparkles' : k === 'blocked' ? 'ban' : k === 'doubt' ? 'help-circle' : k === 'task' ? 'calendar' : 'file' }, meta[0]),
        h('span', { className: 'small' }, h('strong', null, p.assistant || 'Reply helper'), ' · ', p.chat || 'Olena Kovalenko', ' · ', p.time || '3 min ago'),
        k === 'draft' && h(TtlCountdown, { minutes: p.ttl == null ? 42 : p.ttl })),
      p.original && h('blockquote', { className: 'tx-inbox-orig' }, p.original),
      p.proposal && h('div', { className: 'tx-inbox-proposal' }, p.generated && h(MediaThumb, { kind: 'generated' }), p.proposal),
      p.reason && h('p', { className: 'small tx-muted' }, p.reason),
      h('footer', { className: 'tx-inbox-actions' },
        k === 'draft' && [h(Button, { key: 'a', variant: 'primary', icon: 'send', kbd: 'Enter' }, 'Approve and send'), h(Button, { key: 'e', icon: 'pencil', kbd: 'E' }, 'Edit'), h(Button, { key: 'x', variant: 'ghost', icon: 'x', kbd: 'X' }, 'Dismiss')],
        k === 'doubt' && [h(FeedbackToggle, { key: 'f' }), h(Button, { key: 'w', variant: 'ghost', icon: 'help-circle' }, 'Why?')],
        k === 'blocked' && [h(Button, { key: 'r' }, 'Review'), h(Button, { key: 'w', variant: 'ghost', icon: 'help-circle' }, 'Why?')],
        k === 'note' && h(Button, { variant: 'ghost', icon: 'check' }, 'Got it'),
        k === 'task' && [h(Button, { key: 'd', variant: 'primary', icon: 'check' }, 'Mark done'), h(Button, { key: 's', variant: 'ghost', icon: 'clock' }, 'Snooze')]));
  }
  var VERDICT = { yes: ['Clearly yes', 'success', 'circle-check'], likely: ['Likely yes', 'info', 'thumb-up'], unsure: ['Not sure', 'warning', 'help-circle'], no: ['Clearly no', 'neutral', 'x'] };
  function Verdict(p) {
    var v = VERDICT[p.value || 'yes'];
    return h('span', { title: p.confidence != null ? 'Confidence ' + Math.round(p.confidence * 100) + '%' : undefined }, h(Badge, { tone: v[1], icon: v[2] }, v[0]));
  }
  function TtlCountdown(p) {
    var m = p.minutes;
    var st = p.state || (m < 0 ? 'expired' : m < 15 ? 'soon' : 'ok');
    if (p.stale) st = 'stale';
    var label = st === 'expired' ? 'Expired' : st === 'stale' ? 'Outdated: new messages since' : (m >= 60 ? Math.floor(m / 60) + ' h ' + (m % 60) + ' min left' : m + ' min left');
    return h('span', { className: 'tx-ttl tx-ttl-' + st, role: 'timer', 'aria-live': 'off' }, h(Icon, { name: st === 'ok' ? 'clock' : 'alert-triangle', size: 14 }), label);
  }
  function UndoBar(p) {
    var init = p.seconds == null ? 10 : p.seconds;
    var s = useState(init), left = s[0], set = s[1];
    useEffect(function () {
      if (!p.live || left <= 0 || p.state) return;
      var t = setTimeout(function () { set(left - 1); }, 1000); return function () { clearTimeout(t); };
    }, [left, p.live, p.state]);
    var st = p.state || (left <= 0 ? 'sent' : 'counting');
    return h('div', { className: 'tx-undo', role: 'status', 'aria-live': 'polite' },
      st === 'counting' && [h('span', { key: 'r', className: 'tx-undo-ring', style: { '--p': (left / 10) } }, left), h('span', { key: 't' }, 'Sending to ' + (p.chat || 'Olena') + '…'), h('button', { key: 'b', className: 'tx-undo-btn' }, 'Undo')],
      st === 'cancelled' && [h(Icon, { key: 'i', name: 'arrow-back-up' }), h('span', { key: 't' }, 'Cancelled. The draft is back in Inbox.')],
      st === 'sent' && [h(Icon, { key: 'i', name: 'check' }), h('span', { key: 't' }, 'Sent to ' + (p.chat || 'Olena') + '.')]);
  }
  function FeedbackToggle(p) {
    var s = useState(p.value || null), v = s[0], set = s[1];
    return h('div', { className: 'tx-feedback', role: 'group', 'aria-label': 'Was the assistant right?' },
      h('button', { className: cx('tx-fb', v === 'yes' && 'is-yes'), 'aria-pressed': v === 'yes', onClick: function () { set(v === 'yes' ? null : 'yes'); } }, h(Icon, { name: 'check', size: 16 }), 'Right'),
      h('button', { className: cx('tx-fb', v === 'no' && 'is-no'), 'aria-pressed': v === 'no', onClick: function () { set(v === 'no' ? null : 'no'); } }, h(Icon, { name: 'x', size: 16 }), 'Wrong'),
      p.trained != null && h('span', { className: 'small tx-muted' }, 'Learned from ' + p.trained));
  }

  /* ---------- Assistants ---------- */
  var ASTATE = { running: ['Running', 'success'], paused: ['Paused', 'neutral'], stopped: ['Paused by you', 'neutral'], budget: ['Budget reached', 'danger'], model: ['Model error', 'danger'], account: ['No account', 'warning'], consent: ['Needs consent', 'warning'] };
  function AssistantCard(p) {
    var st = ASTATE[p.state || 'running'];
    return h('article', { className: cx('tx-card tx-assistant', p.selectable && 'is-selectable') },
      h('header', { className: 'tx-assistant-head' },
        p.selectable && h('input', { type: 'checkbox', className: 'tx-check', 'aria-label': 'Select ' + p.name, defaultChecked: p.selected }),
        h(Avatar, { icon: 'sparkles' }),
        h('div', null, h('div', { className: 'h3' }, p.name || 'Reply helper'), h('div', { className: 'small tx-muted' }, p.summary || 'Drafts replies in Work chats')),
        h(Badge, { tone: st[1] }, st[0])),
      h('dl', { className: 'tx-stats' },
        h('div', null, h('dt', { className: 'overline' }, 'Runs, 7 days'), h('dd', null, p.runs == null ? 128 : p.runs)),
        h('div', null, h('dt', { className: 'overline' }, 'Learned from'), h('dd', null, p.trained == null ? 34 : p.trained)),
        h('div', null, h('dt', { className: 'overline' }, 'Not sure'), h('dd', null, p.doubts == null ? 5 : p.doubts)),
        h('div', null, h('dt', { className: 'overline' }, 'Spent'), h('dd', null, h(Cost, { value: p.spent == null ? 0.84 : p.spent })))),
      !p.selectable && h('footer', { className: 'tx-row' }, h(Button, { size: 'sm', icon: p.state === 'running' || !p.state ? 'player-pause' : 'player-play' }, p.state === 'running' || !p.state ? 'Pause' : 'Resume'), h(Button, { size: 'sm', variant: 'ghost' }, 'Open')));
  }
  var LEVELS = [['observe', 'Observe', 'eye', 'Reads and labels. Never writes anything.'], ['suggest', 'Suggest', 'bulb', 'Drafts replies and actions for you to approve.'], ['act', 'Act', 'bolt', 'Sends on its own within a daily limit, with 10 s to undo.']];
  function AutonomyPicker(p) {
    var s = useState(p.value || 'suggest'), v = s[0], set = s[1];
    var progress = p.progress == null ? 12 : p.progress, unlocked = progress >= 20;
    return h('div', { className: 'tx-autonomy', role: 'radiogroup', 'aria-label': 'How independent is this assistant?' },
      LEVELS.map(function (l) {
        var locked = l[0] === 'act' && !unlocked;
        return h('label', { key: l[0], className: cx('tx-option', v === l[0] && 'is-checked', locked && 'is-locked') },
          h('input', { type: 'radio', name: 'autonomy', value: l[0], checked: v === l[0], disabled: locked, onChange: function () { set(l[0]); } }),
          h(Icon, { name: locked ? 'lock' : l[2] }),
          h('span', null, h('span', { className: 'h4' }, l[1], l[0] === 'suggest' && h('span', { className: 'small tx-muted' }, ' · default')), h('span', { className: 'small tx-muted tx-block' }, l[3]),
            locked && h('span', { className: 'tx-progress' }, h('span', { className: 'tx-progress-track', role: 'progressbar', 'aria-valuemin': 0, 'aria-valuemax': 20, 'aria-valuenow': progress, 'aria-label': 'Clean approvals' }, h('span', { className: 'tx-progress-fill', style: { width: (progress / 20 * 100) + '%' } })), h('span', { className: 'small' }, progress + ' / 20 clean approvals to unlock'))));
      }));
  }
  function Chip(p) { return h('span', { className: cx('tx-chip', p.muted && 'is-muted') }, p.icon && h(Icon, { name: p.icon, size: 14 }), p.children, p.onRemove !== false && h('button', { className: 'tx-chip-x', 'aria-label': 'Remove ' + p.children }, h(Icon, { name: 'x', size: 12 }))); }
  function ScopePicker(p) {
    var sets = p.sets || ['Work', 'Clients'];
    return h('div', { className: 'tx-field' }, h('span', { className: 'tx-label' }, 'Where it works'),
      h('div', { className: 'tx-chips' }, sets.map(function (x) { return h(Chip, { key: x, icon: 'messages' }, x); }), h(Button, { size: 'sm', variant: 'ghost', icon: 'plus' }, 'Chat set')),
      p.privateWarning !== false && h('p', { className: 'small tx-warning-text' }, h(Icon, { name: 'lock', size: 14 }), '2 chats in these sets are private and will be skipped.'));
  }
  var TRIG = [['words', 'Words'], ['meaning', 'Meaning'], ['hybrid', 'Both'], ['schedule', 'Schedule'], ['chain', 'After another assistant'], ['command', '/ai command']];
  function TriggerEditor(p) {
    var s = useState(p.type || 'meaning'), t = s[0], set = s[1];
    return h('div', { className: 'tx-field' }, h('span', { className: 'tx-label' }, 'When it starts'),
      h('div', { className: 'tx-segmented', role: 'tablist' }, TRIG.map(function (x) { return h('button', { key: x[0], role: 'tab', 'aria-selected': t === x[0], className: cx(t === x[0] && 'is-active'), onClick: function () { set(x[0]); } }, x[1]); })),
      t === 'schedule' ? h(ScheduleEditor, null) :
        h(Fragment, null,
          h('textarea', { className: 'tx-input', rows: 2, defaultValue: p.text || 'Someone asks me to review a document or a pull request', 'aria-label': 'Condition in plain words' }),
          h('div', { className: 'tx-qa' }, h('div', { className: 'small' }, h('strong', null, 'Starts when: '), 'the message asks for a review · names a doc or PR'), h('div', { className: 'small' }, h('strong', null, 'Skips when: '), 'it’s a bot notification · you already replied')),
          h(SensitivitySlider, null),
          p.split && h('p', { className: 'small tx-info-text' }, h(Icon, { name: 'bulb', size: 14 }), 'This covers two jobs. Split into two assistants?')));
  }
  function SensitivitySlider(p) {
    return h('label', { className: 'tx-field' }, h('span', { className: 'tx-label' }, 'How often it starts'),
      h('span', { className: 'tx-slider' }, h('span', { className: 'small tx-muted' }, 'More often'), h('input', { type: 'range', min: 0, max: 100, defaultValue: p.value == null ? 50 : p.value }), h('span', { className: 'small tx-muted' }, 'Less often')));
  }
  function ScheduleEditor(p) {
    return h('div', { className: 'tx-row tx-wrap' },
      h('label', { className: 'tx-select' }, h('span', { className: 'tx-visually-hidden' }, 'Repeat'), h('select', { defaultValue: p.repeat || 'weekdays' }, h('option', { value: 'daily' }, 'Every day'), h('option', { value: 'weekdays' }, 'Weekdays'), h('option', { value: 'weekly' }, 'Weekly')), h(Icon, { name: 'selector', size: 16 })),
      h('input', { className: 'tx-input tx-input-time', type: 'time', defaultValue: p.time || '09:00', 'aria-label': 'Time' }),
      h('span', { className: 'small tx-muted' }, 'Europe/Kyiv · next run tomorrow 09:00'));
  }
  function ModelProfilePicker(p) {
    var opts = [['fast', 'Fast and cheap', 0.40], ['balanced', 'Balanced', 1.80], ['careful', 'Careful', 6.20]];
    var s = useState(p.value || 'balanced'), v = s[0], set = s[1];
    return h('div', { className: 'tx-field' }, h('span', { className: 'tx-label' }, 'Model profile'),
      opts.map(function (o) { return h('label', { key: o[0], className: cx('tx-option tx-option-row', v === o[0] && 'is-checked') }, h('input', { type: 'radio', name: 'model', checked: v === o[0], onChange: function () { set(o[0]); } }), h('span', { className: 'h4' }, o[1]), h('span', { className: 'small tx-muted' }, h(Cost, { value: o[2], estimate: true }), ' per 100 runs')); }),
      p.fallback && h('p', { className: 'small tx-warning-text' }, h(Icon, { name: 'alert-triangle', size: 14 }), 'Main model unavailable. Using the fallback for now.'),
      h(Button, { size: 'sm', variant: 'ghost', icon: 'plus' }, 'Create your own'));
  }
  function BudgetField(p) {
    var limit = p.limit || 5, spent = p.spent == null ? 4.2 : p.spent, pct = spent / limit;
    return h('div', { className: 'tx-field' }, h('label', { className: 'tx-label', htmlFor: 'budget-' + (p.period || 'month') }, 'Budget per ' + (p.period || 'month')),
      h('div', { className: 'tx-input-group' }, h('span', null, '$'), h('input', { id: 'budget-' + (p.period || 'month'), className: 'tx-input', inputMode: 'decimal', defaultValue: limit.toFixed(2) })),
      h('span', { className: cx('small', pct >= 0.8 ? 'tx-warning-text' : 'tx-muted') }, pct >= 0.8 && h(Icon, { name: 'alert-triangle', size: 14 }), 'Spent ', h(Cost, { value: spent }), ' of ', h(Cost, { value: limit }), pct >= 0.8 ? ' · 80% reached' : ''));
  }

  /* ---------- Runs and data ---------- */
  var RSTAT = { queued: ['Queued', 'neutral', 'clock'], thinking: ['Thinking', 'info', 'refresh'], waiting: ['Waiting for you', 'warning', 'hand-stop'], done: ['Done', 'success', 'circle-check'], blocked: ['Blocked', 'danger', 'ban'], failed: ['Failed', 'danger', 'alert-circle'], cancelled: ['Cancelled', 'neutral', 'x'], dry: ['Dry run', 'ai', 'eye'] };
  function RunStatus(p) { var r = RSTAT[p.status || 'done']; return h(Badge, { tone: r[1], icon: r[2] }, r[0]); }
  function RunTimeline(p) {
    var steps = p.steps || [['Trigger', 'New message in “Design team” matched “review request”', 'done'], ['Read', '12 recent messages, 1 file', 'done'], ['Decided', 'Draft a reply · Likely yes', 'done'], ['Safety', 'Blocked: reply shares a private link', 'blocked'], ['Did', 'Nothing sent. Sent to Inbox for review', 'waiting']];
    return h('ol', { className: 'tx-timeline' }, steps.map(function (s, i) {
      return h('li', { key: i, className: 'tx-step tx-step-' + s[2] }, h('span', { className: 'tx-step-dot' }, h(Icon, { name: s[2] === 'blocked' ? 'ban' : s[2] === 'waiting' ? 'hand-stop' : 'check', size: 14 })),
        h('details', null, h('summary', null, h('span', { className: 'h4' }, s[0]), h('span', { className: 'small tx-muted' }, ' · ' + s[1])), h('div', { className: 'small tx-code' }, 'model: fast-cheap · 1,284 tokens · ', h(Cost, { value: 0.002 }))));
    }), p.children);
  }
  function TestResults(p) {
    var st = p.state || 'done';
    if (st === 'running') return h(LoadState, { state: 'loading', rows: 3 });
    if (st === 'thin') return h(EmptyState, { kind: 'thin' });
    var col = function (title, tone, items) { return h('div', { className: 'tx-test-col' }, h('div', { className: 'tx-row' }, h(Badge, { tone: tone }, title), h('span', { className: 'small tx-muted' }, items.length)), items.map(function (t, i) { return h('div', { key: i, className: 'tx-test-item' }, h('span', { className: 'small' }, t), h(FeedbackToggle, null)); })); };
    return h('div', { className: 'tx-test' },
      st === 'stale' && h(StatusBanner, { kind: 'triage', text: 'You changed the condition. Results are outdated.', action: 'Re-run' }),
      h('div', { className: 'tx-row small tx-muted' }, 'Last 30 days · 412 messages · ', h(Cost, { value: 0.31 })),
      h('div', { className: 'tx-test-grid' }, col('Would start', 'success', ['Can you review the Q3 deck?', 'PR #42 is ready for a look']), col('Not sure', 'warning', ['Thoughts on this?']), col('Would skip', 'neutral', ['Lunch at 1?', 'CI passed ✓'])));
  }
  function Cost(p) {
    var v = p.value || 0;
    var text = v > 0 && v < 0.01 ? '< $0.01' : (p.estimate ? '≈ ' : '') + '$' + v.toFixed(2);
    return h('span', { className: 'tx-cost', title: '$' + v.toFixed(4) }, text);
  }
  function FilterBar(p) {
    var f = p.filters || [['Chat', 'Any'], ['Account', 'All'], ['Assistant', 'Reply helper'], ['Type', 'Any'], ['Date', 'Last 7 days']];
    return h('div', { className: 'tx-filters', role: 'search' },
      h('label', { className: 'tx-search' }, h(Icon, { name: 'search', size: 16 }), h('input', { className: 'tx-input', placeholder: 'Search', 'aria-label': 'Search' })),
      f.map(function (x) { return h('button', { key: x[0], className: cx('tx-filter', x[1] !== 'Any' && x[1] !== 'All' && 'is-set') }, h('span', { className: 'tx-muted' }, x[0] + ': '), x[1], h(Icon, { name: 'chevron-down', size: 14 })); }),
      h('button', { className: 'tx-link' }, 'Reset all'));
  }
  function DataTable(p) {
    var cols = p.columns || ['When', 'Assistant', 'Action', 'Chat', 'Cost'];
    var rows = p.rows || [['12:04', 'Reply helper', 'Sent reply', 'Olena Kovalenko', 0.002], ['11:52', 'Digest', 'Posted digest', 'Saved messages', 0.03], ['11:40', 'Reply helper', 'Draft dismissed', 'Design team', 0.001]];
    if (!rows.length) return h(EmptyState, { kind: 'none' });
    return h('div', { className: 'tx-table-wrap' },
      h('table', { className: 'tx-table' },
        h('thead', null, h('tr', null, cols.map(function (c, i) { return h('th', { key: c, className: 'overline', 'aria-sort': i === 0 ? 'descending' : undefined }, c, i === 0 && h(Icon, { name: 'chevron-down', size: 12 })); }))),
        h('tbody', null, rows.map(function (r, i) { return h('tr', { key: i }, r.map(function (c, j) { return h('td', { key: j, 'data-label': cols[j] }, typeof c === 'number' ? h(Cost, { value: c }) : c); })); }))),
      h('div', { className: 'tx-table-foot small' }, h('span', { className: 'tx-muted' }, '1–3 of 128'), h('div', { className: 'tx-row' }, h(Button, { size: 'sm', variant: 'ghost', icon: 'download' }, 'Export CSV'), h(Button, { size: 'sm', ariaLabel: 'Next page', icon: 'chevron-right' }))));
  }
  function ChatPicker(p) {
    return h('div', { className: 'tx-card tx-chatpicker' },
      h('label', { className: 'tx-search' }, h(Icon, { name: 'search', size: 16 }), h('input', { className: 'tx-input', placeholder: 'Find a chat', 'aria-label': 'Find a chat' })),
      h('div', { className: 'tx-tabs small' }, h('button', { className: 'is-active' }, 'All'), h('button', null, 'Work'), h('button', null, 'Family')),
      h('div', null,
        [['Design team', 210, true], ['Olena Kovalenko', 30, true], ['Mom', 350, false, true], ['Clients', 120, false]].map(function (c) {
          return h('label', { key: c[0], className: cx('tx-pick', c[3] && 'is-disabled') }, h('input', { type: p.single ? 'radio' : 'checkbox', name: 'pick', className: 'tx-check', defaultChecked: c[2], disabled: c[3] }), h(ChatRow, { name: c[0], hue: c[1], private: c[3], disabled: c[3], preview: c[3] ? '' : 'Last message 2 h ago', time: '' }));
        })),
      h('div', { className: 'tx-row small tx-muted' }, '2 selected'));
  }

  /* ---------- Forms and feedback ---------- */
  function OnboardingSteps(p) {
    var steps = p.steps || ['Sign in', 'Telegram', 'AI consent', 'Private zone', 'Bot', 'First assistant'];
    var cur = p.current == null ? 2 : p.current;
    return h('div', { className: 'tx-onb' },
      h('ol', { className: 'tx-onb-steps', 'aria-label': 'Setup progress' }, steps.map(function (s, i) { return h('li', { key: s, className: cx(i < cur && 'is-done', i === cur && 'is-current'), 'aria-current': i === cur ? 'step' : undefined }, h('span', { className: 'tx-onb-dot' }, i < cur ? h(Icon, { name: 'check', size: 12 }) : i + 1), h('span', { className: 'small' }, s)); })),
      h('div', { className: 'tx-row tx-between' }, h(Button, { variant: 'ghost', icon: 'arrow-back-up' }, 'Back'), h('div', { className: 'tx-row' }, p.skippable && h(Button, { variant: 'ghost' }, 'Skip for now'), h(Button, { variant: 'primary' }, 'Continue'))));
  }
  function CodeInput(p) {
    var st = p.state || 'input', code = (p.value || '4821').split('');
    return h('div', { className: 'tx-field' }, h('span', { className: 'tx-label', id: 'code-l' }, 'Code from Telegram'),
      h('div', { className: cx('tx-code-cells', st === 'invalid' && 'is-invalid'), role: 'group', 'aria-labelledby': 'code-l' }, [0, 1, 2, 3, 4].map(function (i) { return h('input', { key: i, className: 'tx-code-cell', inputMode: 'numeric', maxLength: 1, autoComplete: i === 0 ? 'one-time-code' : 'off', defaultValue: code[i] || '', 'aria-label': 'Digit ' + (i + 1) }); })),
      st === 'invalid' && h('span', { className: 'small tx-danger' }, h(Icon, { name: 'alert-circle', size: 14 }), 'That code is wrong. Check the latest message from Telegram.'),
      st === 'limited' && h('span', { className: 'small tx-warning-text' }, h(Icon, { name: 'clock', size: 14 }), 'Telegram limits sign-in attempts. Try again in 8 min.'),
      st !== 'limited' && h('span', { className: 'small tx-muted' }, 'Send a new code in 0:' + (p.resendIn || 45)));
  }
  function ConfirmDialog(p) {
    var danger = p.variant === 'destructive';
    return h('div', { className: 'tx-dialog', role: 'alertdialog', 'aria-labelledby': 'dlg-t' },
      h('h3', { className: 'h3', id: 'dlg-t' }, p.title || (danger ? 'Delete “Reply helper”?' : 'Disconnect Work account?')),
      h('p', null, p.consequence || (danger ? 'Its history and 34 learned examples will be deleted. This can’t be undone.' : '3 assistants that use this account will stop.')),
      danger && h('label', { className: 'tx-field' }, h('span', { className: 'tx-label' }, 'Type Reply helper to confirm'), h('input', { className: 'tx-input' })),
      h('div', { className: 'tx-row tx-end' }, h(Button, { variant: 'ghost' }, 'Cancel'), h(Button, { variant: danger ? 'danger' : 'primary' }, p.confirm || (danger ? 'Delete assistant' : 'Disconnect'))));
  }
  var EMPTY = { first: ['sparkles', 'No assistants yet.', 'Create your first assistant'], done: ['circle-check', 'Nothing needs you right now.', 'Open chats'], none: ['search', 'Nothing matches these filters.', 'Reset filters'], blocked: ['player-pause', 'Assistants are paused, so nothing new arrives.', 'Resume all'], thin: ['history', 'Not enough history to test yet. Try a wider chat set.', 'Change scope'] };
  function EmptyState(p) {
    var e = EMPTY[p.kind || 'done'];
    return h('div', { className: 'tx-empty' }, h('span', { className: 'tx-empty-art' }, h(Icon, { name: e[0], size: 32 })), h('p', null, p.text || e[1]), h(Button, { variant: 'primary' }, p.action || e[2]));
  }
  function LoadState(p) {
    var st = p.state || 'loading';
    if (st === 'loading') return h('div', { className: 'tx-skeleton', 'aria-busy': true, 'aria-label': 'Loading' }, Array.from({ length: p.rows || 3 }).map(function (_, i) { return h('div', { key: i, className: 'tx-skel-row' }, h('span', { className: 'tx-skel tx-skel-circle' }), h('span', { className: 'tx-skel-lines' }, h('span', { className: 'tx-skel', style: { width: '40%' } }), h('span', { className: 'tx-skel', style: { width: '75%' } }))); }));
    return h('div', { className: 'tx-empty', role: 'alert' }, h('span', { className: 'tx-empty-art is-danger' }, h(Icon, { name: st === 'offline' ? 'wifi-off' : 'alert-circle', size: 32 })), h('p', null, st === 'offline' ? 'You’re offline. We’ll reconnect when the network is back.' : 'Couldn’t load runs. The server didn’t respond.'), h(Button, { icon: 'refresh' }, 'Try again'));
  }
  var TOAST = { info: ['info-circle', 'Settings saved.'], success: ['circle-check', 'Assistant resumed.'], error: ['alert-circle', 'Couldn’t save. Check your connection and try again.'], draft: ['sparkles', 'New draft for Olena Kovalenko.'] };
  function Toast(p) {
    var t = TOAST[p.kind || 'info'];
    return h('div', { className: 'tx-toast tx-toast-' + (p.kind || 'info'), role: p.kind === 'error' ? 'alert' : 'status' }, h(Icon, { name: t[0] }), h('span', null, p.text || t[1]), (p.kind === 'draft' || p.action) && h('button', { className: 'tx-toast-action' }, p.action || 'Open'), h('button', { className: 'tx-iconbtn', 'aria-label': 'Dismiss' }, h(Icon, { name: 'x', size: 16 })));
  }

  /* ---------- Telegram ---------- */
  function BotMessage(p) {
    var st = p.state || 'buttons';
    var btns = p.buttons || [['✓ Send', '✎ Edit'], ['✗ Dismiss', 'Why?']];
    return h('div', { className: 'tg' },
      h('div', { className: 'tg-bubble' },
        st === 'pinned' && h('div', { className: 'tg-pin small' }, 'Pinned message'),
        h('div', { className: 'tg-sign' }, (p.assistant || 'Reply helper') + ' · ' + (p.account || 'Work')),
        h('div', { className: 'tg-body' }, p.text || (st === 'pinned' ? '3 drafts waiting · 1 needs a check · 2 tasks today' : 'Olena: “Can you review the Q3 deck today?”\nDraft: “Sure, I’ll send comments by 5 pm.”')),
        st === 'done' ? h('div', { className: 'tg-summary' }, '✓ Sent at 12:04 · undo expired') : h('a', { className: 'tg-link', href: '#' }, 'Open in teleX'),
        h('div', { className: 'tg-time' }, '12:03')),
      st === 'buttons' && h('div', { className: 'tg-kb' }, btns.map(function (r, i) { return h('div', { key: i, className: 'tg-kb-row' }, r.map(function (b) { return h('button', { key: b, className: 'tg-kb-btn' }, b); })); })));
  }


  /* ---------- Metrics (C-38…C-41) ---------- */
  var SERIES = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)'];
  function seriesColor(i) { return i < SERIES.length ? SERIES[i] : 'var(--chart-other)'; }
  function niceMax(v) { if (v <= 0) return 1; var p = Math.pow(10, Math.floor(Math.log10(v))), n = v / p; return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p; }
  function fmt(v, unit) { if (unit === '$') return v > 0 && v < 0.01 ? '< $0.01' : '$' + (v >= 100 ? Math.round(v).toLocaleString('en-US') : v.toFixed(2)); if (unit === 's') return v.toFixed(1) + ' s'; if (unit === '%') return v.toFixed(1) + '%'; return Math.round(v).toLocaleString('en-US'); }

  function Sparkline(p) {
    var d = p.data || [], w = 120, hgt = 32, max = Math.max.apply(null, d.concat([0.0001])), min = Math.min.apply(null, d.concat([max]));
    var x = function (i) { return 4 + i * (w - 8) / Math.max(1, d.length - 1); }, y = function (v) { return hgt - 4 - (v - min) / (max - min || 1) * (hgt - 8); };
    var line = d.map(function (v, i) { return (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1); }).join('');
    return h('svg', { className: 'tx-spark', viewBox: '0 0 ' + w + ' ' + hgt, preserveAspectRatio: 'none', 'aria-hidden': true },
      h('path', { d: line + 'L' + x(d.length - 1) + ' ' + hgt + 'L' + x(0) + ' ' + hgt + 'Z', fill: 'var(--chart-1)', fillOpacity: 0.1, stroke: 'none' }),
      h('path', { d: line, fill: 'none', stroke: 'var(--chart-1)', strokeWidth: 2, strokeLinejoin: 'round', strokeLinecap: 'round', vectorEffect: 'non-scaling-stroke' }));
  }
  function KpiTile(p) {
    if (p.empty) return h('div', { className: 'tx-card tx-kpi' }, h('span', { className: 'small tx-muted' }, p.label), h('span', { className: 'tx-kpi-value tx-muted' }, '—'), h('span', { className: 'small tx-muted' }, 'Not enough data yet'));
    var dlt = p.delta || 0, up = dlt > 0, flat = Math.abs(dlt) < 0.5;
    var good = flat ? null : (up === (p.better !== 'down'));
    var word = flat ? 'No change' : (up ? '+' : '−') + Math.abs(dlt).toFixed(0) + '% ' + (good ? 'better' : 'worse');
    return h('a', { className: 'tx-card tx-kpi', href: p.href || '#runs', 'aria-label': p.label + ': ' + p.value + ', ' + word + ' ' + (p.compare || 'vs previous 7 days') },
      h('span', { className: 'small tx-muted' }, p.label),
      h('span', { className: 'tx-kpi-row' }, h('span', { className: 'tx-kpi-value' }, p.value), p.trend && h(Sparkline, { data: p.trend })),
      h('span', { className: cx('small tx-kpi-delta', good === true && 'is-good', good === false && 'is-bad') },
        h(Icon, { name: flat ? 'minus' : (up ? 'trending-up' : 'trending-down'), size: 16 }), word, h('span', { className: 'tx-muted' }, ' ' + (p.compare || 'vs previous 7 days'))));
  }

  function ChartCard(p) {
    var s = useState(false), table = s[0], setTable = s[1];
    return h('figure', { className: 'tx-card tx-chart' },
      h('figcaption', { className: 'tx-chart-head' },
        h('span', null, h('span', { className: 'h3 tx-block' }, p.title), p.subtitle && h('span', { className: 'small tx-muted' }, p.subtitle)),
        h('button', { className: 'tx-iconbtn', 'aria-pressed': table, 'aria-label': table ? 'Show chart' : 'Show as table', title: table ? 'Show chart' : 'Show as table', onClick: function () { setTable(!table); } }, h(Icon, { name: table ? 'chart-bar' : 'table', size: 18 }))),
      p.legend, table ? p.table : p.children);
  }
  function Legend(p) {
    if (!p.items || p.items.length < 2) return null;
    return h('ul', { className: 'tx-legend small' }, p.items.map(function (n, i) { var sl = p.slots && p.slots[i] != null ? p.slots[i] : i; return h('li', { key: n }, h('span', { className: cx('tx-swatch', p.line && 'is-line'), style: { background: seriesColor(sl) } }), n); }));
  }

  function TimeChart(p) {
    var labels = p.labels || ['Sep 24', 'Sep 25', 'Sep 26', 'Sep 27', 'Sep 28', 'Sep 29', 'Sep 30'];
    var series = p.series || [{ name: 'Reply helper', data: [42, 51, 38, 20, 12, 47, 55] }, { name: 'Weekly digest', data: [4, 4, 4, 0, 0, 4, 4] }, { name: 'Deadline catcher', data: [18, 22, 16, 6, 3, 19, 24] }];
    var unit = p.unit || '', mode = p.mode || 'stacked';
    var hv = useState(null), hover = hv[0], setHover = hv[1];
    var W = 640, H = 220, L = 44, R = 12, T = 12, B = 28, pw = W - L - R, ph = H - T - B;
    var totals = labels.map(function (_, i) { return series.reduce(function (a, s) { return a + (mode === 'stacked' ? s.data[i] : 0); }, 0); });
    var peak = mode === 'stacked' ? Math.max.apply(null, totals) : Math.max.apply(null, series.map(function (s) { return Math.max.apply(null, s.data); }));
    if (p.budget) peak = Math.max(peak, p.budget);
    var max = niceMax(peak), ticks = [0, max / 4, max / 2, 3 * max / 4, max];
    var band = pw / labels.length, bw = Math.min(24, band * 0.5);
    var y = function (v) { return T + ph - v / max * ph; }, cxp = function (i) { return L + band * i + band / 2; };
    var marks = [];
    if (mode === 'stacked') labels.forEach(function (_, i) {
      var acc = 0, lastIdx = -1; series.forEach(function (s, k) { if (s.data[i] > 0) lastIdx = k; });
      series.forEach(function (s, k) {
        var v = s.data[i]; if (!v) return;
        var y0 = y(acc), y1 = y(acc + v); acc += v;
        var top = y1 + (k === lastIdx ? 0 : 1), bot = y0 - (acc - v === 0 ? 0 : 1), hh = Math.max(0, bot - top), r = k === lastIdx ? Math.min(4, hh) : 0, x0 = cxp(i) - bw / 2;
        marks.push(h('path', { key: i + '-' + k, fill: seriesColor(s.slot != null ? s.slot : k), opacity: hover != null && hover !== i ? 0.55 : 1, d: 'M' + x0 + ' ' + bot + 'V' + (top + r) + (r ? 'Q' + x0 + ' ' + top + ' ' + (x0 + r) + ' ' + top : '') + 'H' + (x0 + bw - r) + (r ? 'Q' + (x0 + bw) + ' ' + top + ' ' + (x0 + bw) + ' ' + (top + r) : '') + 'V' + bot + 'Z' }));
      });
    });
    else series.forEach(function (s, k) {
      var d = s.data.map(function (v, i) { return (i ? 'L' : 'M') + cxp(i) + ' ' + y(v); }).join('');
      marks.push(h('path', { key: 'l' + k, d: d, fill: 'none', stroke: seriesColor(s.slot != null ? s.slot : k), strokeWidth: 2, strokeLinejoin: 'round', strokeLinecap: 'round' }));
      var li = s.data.length - 1;
      marks.push(h('circle', { key: 'e' + k, cx: cxp(li), cy: y(s.data[li]), r: 4, fill: seriesColor(s.slot != null ? s.slot : k), stroke: 'var(--bg-surface)', strokeWidth: 2 }));
    });
    var tip = hover != null && h('div', { className: 'tx-tooltip', role: 'status', style: { left: (cxp(hover) / W * 100) + '%' } },
      h('div', { className: 'small tx-muted' }, labels[hover]),
      series.map(function (s, k) { return h('div', { key: k, className: 'tx-tip-row' }, h('span', { className: 'tx-tip-key', style: { background: seriesColor(s.slot != null ? s.slot : k) } }), h('strong', null, fmt(s.data[hover], unit)), h('span', { className: 'small tx-muted' }, s.name)); }),
      mode === 'stacked' && h('div', { className: 'tx-tip-row tx-tip-total small' }, 'Total ', h('strong', null, fmt(totals[hover], unit))));
    var svg = h('div', { className: 'tx-plot' },
      h('svg', { viewBox: '0 0 ' + W + ' ' + H, role: 'img', 'aria-label': p.title + '. Use the table button for exact values.', onPointerLeave: function () { setHover(null); } },
        h('g', { 'aria-hidden': true }, ticks.map(function (t, i) { return h('g', { key: i }, h('line', { x1: L, x2: W - R, y1: y(t), y2: y(t), stroke: 'var(--border)', strokeWidth: 1 }), h('text', { x: L - 8, y: y(t) + 4, textAnchor: 'end', className: 'tx-axis' }, fmt(t, unit === '$' ? '$' : ''))); }),
          labels.map(function (l, i) { return h('text', { key: l, x: cxp(i), y: H - 8, textAnchor: 'middle', className: 'tx-axis' }, l); })),
        marks,
        p.budget && h('g', null, h('line', { x1: L, x2: W - R, y1: y(p.budget), y2: y(p.budget), stroke: 'var(--warning-text)', strokeWidth: 1.5, strokeDasharray: '4 3' }), h('text', { x: W - R, y: y(p.budget) - 6, textAnchor: 'end', className: 'tx-axis tx-axis-strong' }, 'Daily budget ' + fmt(p.budget, unit))),
        labels.map(function (_, i) { return h('rect', { key: 'hit' + i, x: L + band * i, y: T, width: band, height: ph, fill: 'transparent', tabIndex: 0, 'aria-label': labels[i] + ': ' + series.map(function (s) { return s.name + ' ' + fmt(s.data[i], unit); }).join(', '), onPointerEnter: function () { setHover(i); }, onFocus: function () { setHover(i); }, onBlur: function () { setHover(null); } }); })),
      tip);
    var table = h('div', { className: 'tx-table-wrap' }, h('table', { className: 'tx-table' },
      h('thead', null, h('tr', null, h('th', { className: 'overline' }, 'Day'), series.map(function (s) { return h('th', { key: s.name, className: 'overline' }, s.name); }))),
      h('tbody', null, labels.map(function (l, i) { return h('tr', { key: l }, h('td', null, l), series.map(function (s) { return h('td', { key: s.name }, fmt(s.data[i], unit)); })); }))));
    return h(ChartCard, { title: p.title || 'Runs per day', subtitle: p.subtitle || 'All assistants · last 7 days', legend: h(Legend, { items: series.map(function (s) { return s.name; }), slots: series.map(function (s) { return s.slot; }), line: mode === 'line' }), table: table }, svg);
  }

  function Breakdown(p) {
    var raw = (p.items || [{ name: 'Reply helper', value: 3.84, slot: 0 }, { name: 'Deadline catcher', value: 1.12, slot: 2 }, { name: 'Weekly digest', value: 0.62, slot: 1 }, { name: 'Triage (quick check)', value: 0.41, slot: 3 }, { name: 'Morning digest', value: 0.2, slot: 4 }, { name: 'Translator', value: 0.06 }, { name: 'Tagger', value: 0.03 }]).slice();
    var items = raw.slice(0, 5); if (raw.length > 5) items.push({ name: 'Other (' + (raw.length - 5) + ')', value: raw.slice(5).reduce(function (a, x) { return a + x.value; }, 0), other: true });
    var unit = p.unit || '$', max = Math.max.apply(null, items.map(function (x) { return x.value; })), total = items.reduce(function (a, x) { return a + x.value; }, 0);
    var table = h('div', { className: 'tx-table-wrap' }, h('table', { className: 'tx-table' }, h('thead', null, h('tr', null, h('th', { className: 'overline' }, p.dimension || 'Assistant'), h('th', { className: 'overline' }, 'Value'), h('th', { className: 'overline' }, 'Share'))),
      h('tbody', null, items.map(function (x) { return h('tr', { key: x.name }, h('td', null, x.name), h('td', null, fmt(x.value, unit)), h('td', null, Math.round(x.value / total * 100) + '%')); }))));
    return h(ChartCard, { title: p.title || 'Spend by assistant', subtitle: p.subtitle || 'Last 7 days · ' + fmt(total, unit) + ' total', table: table },
      h('ul', { className: 'tx-breakdown' }, items.map(function (x, i) {
        return h('li', { key: x.name, tabIndex: 0, title: x.name + ': ' + fmt(x.value, unit) + ' (' + Math.round(x.value / total * 100) + '%)' },
          h('span', { className: 'tx-bd-name small' }, x.name),
          h('span', { className: 'tx-bd-track' }, h('span', { className: 'tx-bd-bar', style: { width: Math.max(1, x.value / max * 100) + '%', background: x.other ? 'var(--chart-other)' : seriesColor(x.slot != null ? x.slot : i) } })),
          h('span', { className: 'tx-bd-val small' }, fmt(x.value, unit)));
      })));
  }

  var PERIODS = [['24h', '24 hours'], ['7d', '7 days'], ['30d', '30 days'], ['custom', 'Custom…']];
  function PeriodPicker(p) {
    var s = useState(p.value || '7d'), v = s[0], set = s[1];
    return h('div', { className: 'tx-row tx-wrap tx-period' },
      h('div', { className: 'tx-segmented', role: 'radiogroup', 'aria-label': 'Period' }, PERIODS.map(function (x) { return h('button', { key: x[0], role: 'radio', 'aria-checked': v === x[0], className: cx(v === x[0] && 'is-active'), onClick: function () { set(x[0]); } }, x[1]); })),
      h('label', { className: 'tx-row small' }, h('input', { type: 'checkbox', className: 'tx-check', defaultChecked: p.compare !== false }), 'Compare with previous period'),
      h('span', { className: 'small tx-muted' }, h(Icon, { name: 'clock', size: 14 }), ' ' + (p.tz || 'Europe/Kyiv')));
  }

  window.TeleX = { Icon: Icon, Button: Button, Badge: Badge, Avatar: Avatar, Chip: Chip,
    AppShell: AppShell, AccountSwitcher: AccountSwitcher, StopAllButton: StopAllButton, StatusBanner: StatusBanner, SidePanel: SidePanel,
    ChatRow: ChatRow, Message: Message, AiTrace: AiTrace, Composer: Composer, MediaThumb: MediaThumb,
    InboxCard: InboxCard, Verdict: Verdict, TtlCountdown: TtlCountdown, UndoBar: UndoBar, FeedbackToggle: FeedbackToggle,
    AssistantCard: AssistantCard, AutonomyPicker: AutonomyPicker, ScopePicker: ScopePicker, TriggerEditor: TriggerEditor, SensitivitySlider: SensitivitySlider, ScheduleEditor: ScheduleEditor, ModelProfilePicker: ModelProfilePicker, BudgetField: BudgetField,
    RunStatus: RunStatus, RunTimeline: RunTimeline, TestResults: TestResults, Cost: Cost, FilterBar: FilterBar, DataTable: DataTable, ChatPicker: ChatPicker,
    OnboardingSteps: OnboardingSteps, CodeInput: CodeInput, ConfirmDialog: ConfirmDialog, EmptyState: EmptyState, LoadState: LoadState, Toast: Toast,
    BotMessage: BotMessage, Sparkline: Sparkline, KpiTile: KpiTile, TimeChart: TimeChart, Breakdown: Breakdown, PeriodPicker: PeriodPicker };
})();
