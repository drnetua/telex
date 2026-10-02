# TimeChart

C-39 · Activity or spend over time: stacked columns by assistant (default) or lines.

- One y-axis only. Columns at most 24px wide with a 4px rounded top and 2px gaps between segments; lines 2px with an end dot.
- Hover or keyboard focus on a day shows one tooltip with every series and the total.
- Always a legend for two or more series; the table button swaps the chart for an accessible table.
- Spend charts can show the daily budget as a dashed reference line.
- Series colours follow the assistant everywhere (`chart-1`…`chart-5`, then `chart-other`), never its rank: pass `slot` per series when a chart shows a subset.
