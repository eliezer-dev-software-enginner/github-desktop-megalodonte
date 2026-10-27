# Megalodonte Git

Cliente Git desktop nativo, orientado por dados e criado com o framework Megalodonte. A view inteira é JSONC (`src/main/resources/views/desktop.view.jsonc`), com lógica em `Desktop.java`, serviços Git em `GitService.java` e launcher compartilhado `megalodonte.script.ScriptLauncher`. Não usa WebView nem componentes JavaFX no código consumidor.

Abre, cria e clona repositórios; mostra alterações e diff, índice e histórico; faz commits de arquivos selecionados sem capturar alterações alheias já preparadas; cria/troca branch; executa fetch, pull fast-forward, push e stash. Listas, diff e controles de mídia vêm de `megalodonte-components`, configurados e vinculados a dados por `megalodonte-jsonc-ui`. O tema escuro usa os tokens de cor do `renderer.css` do GitHub Desktop instalado; o ícone do app está ligado à janela e ao pacote. A sincronização da branch mostra os commits à frente do upstream e oferece o push mesmo quando não há arquivos alterados; a contagem usa as referências do último fetch. Operações Git rodam fora da thread de UI.

O Git do sistema deve estar no PATH. Credenciais de remotos seguem a configuração Git existente, com prompt interativo desabilitado. Se a autenticação HTTPS do GitHub falhar, fetch, pull e push tentam novamente por SSH usando a chave já configurada, sem alterar a URL salva do remoto. Preferências de repositórios recentes ficam em `~/.config/megalodonte-git/repositories.json`. O AppImage contém a JVM reduzida e as bibliotecas JavaFX usadas, mas usa o Git instalado no sistema.

## Executar a partir do código-fonte

Para compilar, é necessário um JDK 25 e acesso às dependências Gradle. Com a configuração padrão, o build composto também exige o repositório `megalodonte-script-app` em `../../framework/megalodonte-declarative/megalodonte-script-app`, relativo à raiz deste projeto. As dependências `megalodonte-script-app:0.1.0` e `megalodonte-theme:1.0.0-beta` também precisam estar disponíveis para o Gradle (por exemplo, no Maven local). Consulte o [workflow de empacotamento](.github/workflows/package.yml) para ver como o projeto prepara os repositórios do framework em CI.

No Linux, com o JDK 25 selecionado como Java padrão ou por `JAVA_HOME`, execute:

```bash
./gradlew test run
```

Na interface, abra uma pasta que já contenha um repositório Git, informe uma URL Git e uma pasta de destino nova para clonar, ou informe uma pasta para criar um repositório. Selecione arquivos na lista de alterações, escreva o resumo do commit e use o botão de commit. As ações de branch, stash e sincronização estão disponíveis nos controles da janela.

## Empacotar no Linux

O empacotamento também requer Python 3 e a biblioteca `megalodonte-packaging`, disponível para importação ou em `../../framework/megalodonte-packaging` (ou apontada por `MEGALODONTE_PACKAGING_HOME`). A criação do AppImage requer as ferramentas e os módulos JavaFX nativos usados pelo empacotador; o [workflow](.github/workflows/package.yml) mostra a preparação em Linux x86_64.

```bash
python3 scripts/package.py build appimage --run
```

Esse comando usa `megalodonte-packaging` para criar `dist/Megalodonte Git-0.1.3-x86_64.AppImage` e executá-lo. Para apenas empacotar, omita `--run`. `APPIMAGETOOL` pode apontar para uma ferramenta alternativa; por padrão, o empacotador procura a ferramenta instalada ou a do workspace. O build usa o projeto composto local quando ele está presente.

[Resultados da inspeção REA](docs/REA_FINDINGS.md) · [Perfil reutilizável](docs/reconstruction.profile.jsonc) · [Skill reutilizável](skills/megalodonte-app-reconstruction/SKILL.md).

O layout limita a janela à área visível da tela e deixa os painéis estreitos com rolagem vertical. O teste `DesktopLayoutTest` verifica a interface em 1366×684 e 1050×560.

## GitHub Actions

O workflow [Package native installers](.github/workflows/package.yml) executa os testes em uma tela virtual e gera AppImage e `.deb` no Linux e `.msi` no Windows com `megalodonte-packaging` a cada push no `main`. Também pode ser iniciado manualmente em **Actions → Package native installers → Run workflow**. Os instaladores ficam nos artefatos `megalodonte-git-linux` e `megalodonte-git-windows` da execução.

O workflow baixa `megalodonte-script-app`, `megalodonte-jsonc-ui`, `megalodonte-core`, `megalodonte-base`, `megalodonte-components`, `megalodonte-theme` e `megalodonte-packaging` dos repositórios públicos do projeto para montar o build composto no runner.

Uma tag `vX.Y.Z` que corresponda à versão de `packaging.toml` executa os dois builds e publica uma GitHub Release com AppImage, `.deb` e `.msi`. Exemplo: `git tag v0.1.3 && git push origin v0.1.3`. A publicação só ocorre após ambos os jobs de empacotamento terminarem com sucesso.

## Memória e tamanho no Linux

Medição local em 12/10/2026, Linux x86_64, com o GitHub Desktop **3.4.13-linux1** (Flatpak `io.github.shiftey.Desktop`) e o Megalodonte Git **0.1.2** (AppImage) abertos ao mesmo tempo, em repouso. Para memória, somei a **PSS** de todos os processos de cada aplicativo em `/proc/*/smaps_rollup` e usei a mediana de cinco leituras, espaçadas em cinco segundos. PSS distribui as páginas compartilhadas entre os processos e evita a dupla contagem que ocorre ao somar RSS.

| Medida | GitHub Desktop original | Megalodonte Git | Redução medida |
| --- | ---: | ---: | ---: |
| RAM em repouso (PSS) | 347,3 MiB | 189,3 MiB | 158,0 MiB (45,5%) |
| Arquivos descompactados do aplicativo | 429,6 MiB | 191,1 MiB | 238,5 MiB (55,5%) |

Para o tamanho em disco, medi com `du -sb` o diretório `files` da instalação Flatpak e o conteúdo extraído do AppImage com `--appimage-extract`. O AppImage distribuído tem **64,6 MiB** compactado. A comparação de arquivos descompactados não inclui o runtime compartilhado do Flatpak; o AppImage já traz sua JVM reduzida. Os números refletem esta máquina, estas versões e o estado de repouso, podendo variar com repositório aberto, tarefas em execução e sistema operacional.
