# Workspace Megalodonte

Raiz: `/home/eliezer/Desktop/dev/dev/megalodonte-world`.

- Exemplo JSONC + scripts: `megalodonte-declarative/megalodonte-cliente`.
- Orquestração, ciclo de vida e trabalho assíncrono: `megalodonte-declarative/megalodonte-script-app`.
- Schema, renderer e componentes DataList/DiffView: `megalodonte-declarative/megalodonte-jsonc-ui`.
- Tema e componentes: `megalodonte-ecossystem/megalodonte-libs`.
- Exemplo Git e empacotamento: `megalodonte-declarative/megalodonte-git`.

Usar Java 25 e os Gradle composite builds locais durante desenvolvimento. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 bash gradlew test installDist --offline`; para publicar biblioteca local: `bash gradlew publishToMavenLocal`. O empacotamento AppImage usa `scripts/package.py` e `packaging.toml` do `megalodonte-packaging`: `python3 scripts/package.py build appimage --run`. Omita `--run` quando apenas gerar o artefato. Não copie IDs/versionamentos sem verificar o projeto atual.
