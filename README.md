# Megalodonte Git

Cliente Git desktop nativo, orientado por dados e criado com o framework Megalodonte. A view inteira é JSONC (`src/main/resources/views/desktop.view.jsonc`), com lógica em `Desktop.java`, serviços Git em `GitService.java` e launcher compartilhado `megalodonte.script.ScriptLauncher`. Não usa WebView nem componentes JavaFX no código consumidor.

Abre, cria e clona repositórios; mostra alterações e diff, índice e histórico; faz commits de arquivos selecionados sem capturar alterações alheias já preparadas; cria/troca branch; executa fetch, pull fast-forward, push e stash. Listas e diff virtualizados vêm de `megalodonte-jsonc-ui`. Operações Git rodam fora da thread de UI.

O Git do sistema deve estar no PATH. Credenciais de remotos seguem a configuração Git existente, com prompt interativo desabilitado. Preferências de repositórios recentes ficam em `~/.config/megalodonte-git/repositories.json`. O AppImage contém a JVM reduzida e as bibliotecas JavaFX usadas, mas usa o Git instalado no sistema.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 bash gradlew test run
bash scripts/package-appimage.sh
```

O segundo comando cria `build/appimage/MegalodonteGit-x86_64.AppImage` e o abre automaticamente. `MEGALODONTE_NO_OPEN=1` apenas empacota. `APPIMAGETOOL` aponta para uma ferramenta alternativa; o padrão reutiliza a ferramenta já presente no workspace. A build usa composite builds das bibliotecas locais; não precisa de cópias de bibliotecas no projeto.

[Resultados da inspeção REA](docs/REA_FINDINGS.md) · [Perfil reutilizável](docs/reconstruction.profile.jsonc) · [Skill reutilizável](skills/megalodonte-app-reconstruction/SKILL.md).
