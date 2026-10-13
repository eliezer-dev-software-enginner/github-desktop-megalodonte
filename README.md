# Megalodonte Git

Cliente Git desktop nativo, orientado por dados e criado com o framework Megalodonte. A view inteira é JSONC (`src/main/resources/views/desktop.view.jsonc`), com lógica em `Desktop.java`, serviços Git em `GitService.java` e launcher compartilhado `megalodonte.script.ScriptLauncher`. Não usa WebView nem componentes JavaFX no código consumidor.

Abre, cria e clona repositórios; mostra alterações e diff, índice e histórico; faz commits de arquivos selecionados sem capturar alterações alheias já preparadas; cria/troca branch; executa fetch, pull fast-forward, push e stash. Listas, diff e controles de mídia vêm de `megalodonte-components`, configurados e vinculados a dados por `megalodonte-jsonc-ui`. O tema escuro usa os tokens de cor do `renderer.css` do GitHub Desktop instalado; o ícone do app está ligado à janela e ao pacote. A sincronização da branch mostra os commits à frente do upstream e oferece o push mesmo quando não há arquivos alterados; a contagem usa as referências do último fetch. Operações Git rodam fora da thread de UI.

O Git do sistema deve estar no PATH. Credenciais de remotos seguem a configuração Git existente, com prompt interativo desabilitado. Se a autenticação HTTPS do GitHub falhar, fetch, pull e push tentam novamente por SSH usando a chave já configurada, sem alterar a URL salva do remoto. Preferências de repositórios recentes ficam em `~/.config/megalodonte-git/repositories.json`. O AppImage contém a JVM reduzida e as bibliotecas JavaFX usadas, mas usa o Git instalado no sistema.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 bash gradlew test run
python3 scripts/package.py build appimage --run
```

O segundo comando usa `megalodonte-packaging` para criar `dist/Megalodonte Git-0.1.2-x86_64.AppImage` e executá-lo. Para apenas empacotar, omita `--run`. `APPIMAGETOOL` pode apontar para uma ferramenta alternativa; por padrão, o empacotador procura a ferramenta instalada ou a do workspace. A build usa composite builds das bibliotecas locais.

[Resultados da inspeção REA](docs/REA_FINDINGS.md) · [Perfil reutilizável](docs/reconstruction.profile.jsonc) · [Skill reutilizável](skills/megalodonte-app-reconstruction/SKILL.md).

O layout limita a janela à área visível da tela e deixa os painéis estreitos com rolagem vertical. O teste `DesktopLayoutTest` verifica a interface em 1366×684 e 1050×560.

## GitHub Actions

O workflow [Build AppImage](.github/workflows/appimage.yml) executa os testes em uma tela virtual e gera o AppImage com `megalodonte-packaging` a cada push no `main`. Também pode ser iniciado manualmente em **Actions → Build AppImage → Run workflow**. O AppImage fica disponível como artefato `megalodonte-git-linux-x86_64` na execução.

O workflow baixa `megalodonte-script-app`, `megalodonte-jsonc-ui`, `megalodonte-core`, `megalodonte-base`, `megalodonte-components`, `megalodonte-theme` e `megalodonte-packaging` dos repositórios públicos do projeto para montar o build composto no runner.
