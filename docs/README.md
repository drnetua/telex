# teleX — артефакти проєкту

Вивантажено 30 вересня 2026. Живі версії (актуальні, редаговані) — за посиланнями нижче; цей архів — знімок.

## docs/
| Файл | Що це | Живий артефакт |
|---|---|---|
| `01-tech-spec.md` | Технічний документ: ідея, концепція System 1/2, глосарій, стек, архітектура (13 модулів Modulith), модель агента, roadmap, NFR і ризики | https://claude.ai/code/artifact/f936a159-0823-4bf3-85fd-fd9b3ea2a255 |
| `02-epics.md` | Вкладка «Епіки»: спільний DoD, 29 епіків із цінністю, фічами, історіями, AC і власним DoD | той самий документ, вкладка «Епіки» |
| `03-product-spec.md` | Продуктова специфікація: персони, 27 юзкейсів, флоу, 38 екранів, 14 шаблонів бота, 41 компонент, обсяг робіт дизайнера, рішення D-01…D-20 | https://claude.ai/code/artifact/5545909a-ed5c-4897-9233-a0b6e3cf95d4 |
| `img/` | Діаграми з документів, PNG | — |

## design-system/
Дизайн-система teleX на Tabler 1.6: `README.md` (brand book), `tokens.json` (світла й темна теми), `components/` (bundle.js — 46 React-компонентів у `window.TeleX`, bundle.css, index.d.ts, README і preview.html на кожен), `assets/` (логотип, Tabler Icons), `previews/` (скріншоти графіків).
Живий артефакт: https://claude.ai/artifact/294iF2F7j2pGFWYtxNA7ux

## designs/scr-80-overview/
Макет екрана «Огляд» у форматі Claude Design (`*.dc.html` + `canvas.json` + копія дизайн-системи в `ds/telex/`). Файли відкриваються в Claude Design; поза ним `.dc.html` не рендеряться як звичайні сторінки.
Живий артефакт: https://claude.ai/artifact/Kp4tYTg4MWNMvEU9WYS79t
