package io.github.fiftieshousewife.dockerfileinventory.report;

final class Style {

    static final String CSS = """
            :root { color-scheme: light; --bg: #fcfcfb; --fg: #1d1d1f; --muted: #5f5e5a; --line: #e4e3df;
                    --tile: #f3f2ef; --track: #ebeae6; --good: #0ca30c; --watch: #c98500; --act: #d03b3b;
                    --neutral: #a3a29b; --series-1: #2a78d6; --c1: #2a78d6; --c2: #eb6834; --c3: #1baf7a;
                    --c4: #eda100; }
            @media (prefers-color-scheme: dark) {
              :root:not([data-theme="light"]) { color-scheme: dark; --bg: #1a1a19; --fg: #f0efec;
                      --muted: #b0afa8; --line: #3a3a38; --tile: #252523; --track: #33332f; --good: #2fbf2f;
                      --watch: #e0a020; --act: #e66767; --neutral: #6e6d68; --series-1: #3987e5; --c1: #3987e5;
                      --c2: #d95926; --c3: #199e70; --c4: #c98500; }
            }
            body { margin: 0 auto; max-width: 1100px; padding: 20px 16px 48px; background: var(--bg);
                   color: var(--fg); font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
            nav { display: flex; flex-wrap: wrap; gap: 4px 18px; padding-bottom: 10px; margin-bottom: 18px;
                  border-bottom: 1px solid var(--line); font-size: 14px; }
            nav a { color: var(--muted); text-decoration: none; } nav a:hover, nav a:focus { color: var(--fg); }
            a { color: var(--series-1); } a:focus-visible, summary:focus-visible { outline: 2px solid var(--series-1);
                outline-offset: 2px; }
            h1 { font-size: 26px; line-height: 1.2; margin: 0 0 6px; letter-spacing: -0.01em; }
            h2 { font-size: 18px; margin: 36px 0 8px; } h3 { font-size: 15px; margin: 20px 0 6px; }
            p { margin: 0 0 10px; max-width: 78ch; } p.lead { font-size: 16px; }
            .note, p.legend { color: var(--muted); font-size: 13px; }
            ul { margin: 0 0 10px; padding-left: 20px; } li { margin: 2px 0; }
            table { border-collapse: collapse; width: 100%; font-variant-numeric: tabular-nums; }
            th, td { text-align: left; padding: 5px 8px; border-bottom: 1px solid var(--line); vertical-align: top; }
            th { font-weight: 600; font-size: 13px; color: var(--muted); }
            td.num, th.num { text-align: right; white-space: nowrap; }
            td.code, code { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 13px;
                            overflow-wrap: anywhere; }
            table.attributes { width: auto; } table.attributes th { padding-right: 24px; }
            .scroll { overflow-x: auto; }
            .strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px;
                     margin: 18px 0 8px; }
            .health { background: var(--tile); border-radius: 8px; padding: 10px 12px; border-left: 5px solid
                      var(--neutral); }
            .health.good { border-left-color: var(--good); } .health.watch { border-left-color: var(--watch); }
            .health.act { border-left-color: var(--act); }
            .health .label { font-size: 13px; color: var(--muted); }
            .health .value { font-size: 26px; font-weight: 650; font-variant-numeric: tabular-nums; }
            .health .state { font-size: 13px; font-weight: 600; }
            .health .icon { display: inline-block; width: 1.2em; text-align: center; }
            .health.good .icon { color: var(--good); } .health.watch .icon { color: var(--watch); }
            .health.act .icon { color: var(--act); } .health.no-data .icon { color: var(--muted); }
            .health .who { font-size: 12px; color: var(--muted); margin-top: 4px; }
            .meter { display: inline-block; width: 120px; height: 8px; border-radius: 4px; background: var(--track);
                     vertical-align: middle; overflow: hidden; }
            .meter span { display: block; height: 100%; background: var(--series-1); border-radius: 4px; }
            .mix { display: inline-flex; gap: 2px; width: 160px; height: 10px; vertical-align: middle; }
            .mix.wide { width: 100%; max-width: 640px; height: 16px; margin: 4px 0 8px; }
            .mix span { display: block; height: 100%; border-radius: 3px; min-width: 2px; }
            .s1 { background: var(--c1); } .s2 { background: var(--c2); } .s3 { background: var(--c3); }
            .s4 { background: var(--c4); }
            .key { display: inline-block; width: 11px; height: 11px; border-radius: 3px; margin: 0 5px 0 10px;
                   vertical-align: -1px; }
            ul.legend-list { list-style: none; padding-left: 0; font-size: 13px; }
            ul.legend-list .key { margin-left: 0; }
            .chip { display: inline-block; padding: 0 7px; border-radius: 10px; background: var(--tile);
                    border: 1px solid var(--line); font-size: 12px; white-space: nowrap; }
            .tier { font-size: 12px; color: var(--muted); border: 1px solid var(--line); border-radius: 4px;
                    padding: 0 5px; }
            .flag { font-size: 12px; font-weight: 600; } .flag span { color: var(--act); }
            details { margin: 4px 0; } details summary { cursor: pointer; }
            details > ul.tree { margin-top: 4px; }
            ul.tree { list-style: none; padding-left: 18px; border-left: 1px solid var(--line); margin-left: 4px; }
            ul.files { font-size: 13px; padding-left: 18px; }
            pre { background: var(--tile); border-radius: 8px; padding: 10px; overflow-x: auto; font-size: 12px; }
            .info { display: inline-block; position: relative; width: 15px; height: 15px; margin-left: 6px;
                    border-radius: 50%; border: 1px solid var(--muted); color: var(--muted); font: 600 10px/13px
                    system-ui, sans-serif; text-align: center; cursor: help; vertical-align: 2px; }
            .info:hover::after, .info:focus::after { content: attr(data-tip); position: absolute; left: 50%;
                    top: 20px; transform: translateX(-50%); width: 260px; padding: 8px 10px; border-radius: 8px;
                    background: var(--fg); color: var(--bg); font: 400 12px/1.4 system-ui, sans-serif;
                    text-align: left; z-index: 10; white-space: normal; }
            svg.chart { width: 100%; height: auto; max-width: 760px; display: block; }
            svg.chart .line { stroke: var(--series-1); stroke-width: 2; stroke-linejoin: round; }
            svg.chart .grid { stroke: var(--line); stroke-width: 1; }
            svg.chart .baseline { stroke: var(--muted); stroke-width: 1; }
            svg.chart .axis { fill: var(--muted); font-size: 11px; }
            svg.chart .label { fill: var(--fg); font-size: 12px; }
            svg.chart .marker circle { fill: var(--series-1); stroke: var(--bg); stroke-width: 2; }
            .pager { display: flex; flex-wrap: wrap; gap: 4px 14px; margin: 10px 0; font-size: 13px; }
            @media (max-width: 600px) { .meter { width: 60px; } .mix { width: 90px; } }
            """;

    private Style() {
    }
}
