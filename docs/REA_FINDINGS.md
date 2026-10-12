# GitHub Desktop instalado: evidência e escopo

Artefato local: Flatpak `io.github.shiftey.Desktop`, versão `3.4.13-linux1`, diretório `.../files/github-desktop/resources/app`. REA `analyze-javascript-application` do diretório extraído, Evidence ID `ev_2fdf8c687406481045cd8dc8b57a6a0c26453e6d77d9b8429c29c2b356c27412`, SHA-256 de raiz `605cf675a99aa557672e299e96c151d46a630c5c4d613d2df29db5fb4ca5517b`. O resultado completo de 55 MB é local e ignorado pelo Git em `analysis/github-desktop-host-evidence.json`.

A análise estática do pacote inventariou 180 arquivos relevantes, analisou 67 JavaScript, visitou 746.620 nós AST e encontrou 616 fatos. `renderer.js.map` contém módulos fonte `app/src/ui/changes/{changes,sidebar}.tsx`, `app/src/ui/history/commit-list.tsx`, `app/src/ui/branches/branches-container.tsx` e `app/src/lib/git/{status,diff,commit,fetch,pull,push,branch,stash}.ts`. Isso demonstra a presença dessas implementações no artefato; não comprova qual caminho de UI foi executado durante uma sessão. A análise preserva 88 limitações, inclusive relacionamentos dinâmicos de IPC e cobertura parcial de bundles de sintaxe. Nenhuma afirmação de paridade total depende dessa evidência.

| Área | Referência observada no pacote | Megalodonte Git |
| --- | --- | --- |
| Repositórios e branches | listagem de repositórios, componentes de branches | recentes, abrir, criar, clonar, listar, criar e trocar branch |
| Alterações e commit | sidebar de alterações e módulos Git de diff/commit | seleção de arquivos, diff, índice, commit de seleção isolada |
| Histórico | lista de commits | histórico paginado e diff do commit |
| Remotos | módulos fetch/pull/push | fetch, pull fast-forward e push via Git CLI |
| Stash | módulo Git stash | guardar e restaurar último stash |
| Recursos avançados | módulos de diff de imagens, rebase, merge, cherry-pick e PRs | ainda não implementados |

A versão Megalodonte executa JavaFX nativo através dos componentes do framework; nenhum WebView foi adicionado. O backend usa o Git do sistema, enquanto o Flatpak analisado embute sua própria distribuição Git. Essa diferença e os recursos avançados ausentes são limites materiais. O REA produziu evidência estática; somente o startup do novo aplicativo foi observado em runtime nesta entrega.
