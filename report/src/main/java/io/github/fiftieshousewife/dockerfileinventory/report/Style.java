package io.github.fiftieshousewife.dockerfileinventory.report;

final class Style {

    static final String CSS = """
            :root { --bg: #ffffff; --fg: #1d1d1f; --muted: #6e6e73; --line: #e5e5ea; --tile: #f5f5f7;
                    --good: #0ca30c; --watch: #fab219; --act: #d03b3b; --series-1: #2a78d6;
                    --neutral: #a3a29b; --c1: #2a78d6; --c2: #eb6834; --c3: #1baf7a; --c4: #eda100;
                    --c5: #e87ba4; --c6: #008300; --c7: #6250d6; --c8: #e34948; }
            @media (prefers-color-scheme: dark) {
              :root { --bg: #1c1c1e; --fg: #f2f2f7; --muted: #a1a1a6; --line: #3a3a3c; --tile: #2c2c2e;
                      --series-1: #3987e5; --neutral: #6e6d68; --c1: #3987e5; --c2: #d95926; --c3: #199e70;
                      --c4: #c98500; --c5: #d55181; --c6: #008300; --c7: #9085e9; --c8: #e66767; }
            }
            body { margin: 0 auto; max-width: 1100px; padding: 24px 16px; background: var(--bg); color: var(--fg);
                   font: 15px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
            h1 { font-size: 24px; margin: 0 0 4px; } h2 { font-size: 18px; margin: 32px 0 8px; }
            p.note { color: var(--muted); margin: 0 0 12px; }
            .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
            .tile { background: var(--tile); border-radius: 10px; padding: 12px 14px; }
            .tile .value { font-size: 26px; font-weight: 600; font-variant-numeric: tabular-nums; }
            .tile .label { color: var(--muted); font-size: 13px; }
            table { border-collapse: collapse; width: 100%; font-variant-numeric: tabular-nums; }
            th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--line); vertical-align: top; }
            td.num, th.num { text-align: right; } td.code { font-family: ui-monospace, monospace; font-size: 13px;
            overflow-wrap: anywhere; }
            .scroll { overflow-x: auto; }
            .strip { margin: 16px 0 24px; }
            svg.chart { width: 100%; height: auto; max-width: 760px; display: block; }
            svg.chart .line { stroke: var(--series-1); stroke-width: 2; stroke-linejoin: round; }
            svg.chart .grid { stroke: var(--line); stroke-width: 1; }
            svg.chart .baseline { stroke: var(--muted); stroke-width: 1; }
            svg.chart .axis { fill: var(--muted); font-size: 11px; }
            svg.chart .label { fill: var(--fg); font-size: 12px; }
            svg.chart .bar.eol, .key.eol { fill: var(--act); background: var(--act); }
            svg.chart .bar.supported, .key.supported { fill: var(--good); background: var(--good); }
            svg.chart .bar.unknown, .key.unknown { fill: var(--neutral); background: var(--neutral); }
            svg.chart .hatch { stroke: var(--bg); stroke-width: 2.5; }
            .key { display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin: 0 6px 0 12px;
                   vertical-align: -1px; }
            .key.hatched { background: repeating-linear-gradient(45deg, var(--neutral) 0 3px, var(--bg) 3px 6px); }
            p.legend { color: var(--muted); font-size: 13px; margin: 0 0 8px -12px; }
            details summary { cursor: pointer; color: var(--muted); font-size: 13px; margin: 8px 0; }
            .s1 { fill: var(--c1); background: var(--c1); } .s2 { fill: var(--c2); background: var(--c2); }
            .s3 { fill: var(--c3); background: var(--c3); } .s4 { fill: var(--c4); background: var(--c4); }
            .s5 { fill: var(--c5); background: var(--c5); } .s6 { fill: var(--c6); background: var(--c6); }
            .s7 { fill: var(--c7); background: var(--c7); } .s8 { fill: var(--c8); background: var(--c8); }
            .info { display: inline-block; position: relative; width: 15px; height: 15px; margin-left: 6px;
                    border-radius: 50%; border: 1px solid var(--muted); color: var(--muted); font: 600 10px/13px
                    system-ui, sans-serif; text-align: center; cursor: help; vertical-align: 2px;
                    text-transform: none; letter-spacing: 0; }
            .info:hover::after, .info:focus::after { content: attr(data-tip); position: absolute; left: 50%;
                    top: 20px; transform: translateX(-50%); width: 260px; padding: 8px 10px; border-radius: 8px;
                    background: var(--fg); color: var(--bg); font: 400 12px/1.4 system-ui, sans-serif;
                    text-align: left; z-index: 10; white-space: normal; }
            .card { position: relative; background: var(--tile); border-radius: 10px; padding: 12px 14px 4px 52px;
                    margin: 0 0 10px; }
            .card .rank { position: absolute; left: 14px; top: 12px; width: 26px; height: 26px; border-radius: 50%;
                          background: var(--series-1); color: #fff; font-weight: 600; text-align: center;
                          line-height: 26px; font-size: 13px; }
            .card p { margin: 0 0 6px; }
            .card pre { background: var(--bg); border-radius: 8px; padding: 10px; overflow-x: auto; font-size: 12px; }
            svg.chart .marker circle { fill: var(--series-1); stroke: var(--bg); stroke-width: 2; }
            .health { background: var(--tile); border-radius: 10px; padding: 10px 14px; border-left: 6px solid; }
            .health.good { border-left-color: var(--good); } .health.watch { border-left-color: var(--watch); }
            .health.act { border-left-color: var(--act); }
            .health .state { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: .04em;
                             color: var(--muted); }
            .health .value { font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
            .health .label { color: var(--muted); font-size: 13px; }
            ul.tree, ul.tree ul { list-style: none; padding-left: 20px; } ul.tree { padding-left: 0; }
            ul.tree li { margin: 4px 0; } ul.tree code { font-size: 13px; overflow-wrap: anywhere; }
            """;

    private Style() {
    }
}
