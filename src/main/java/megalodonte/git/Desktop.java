package megalodonte.git;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import megalodonte.script.ViewScript;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

/** Application logic only. Every visual and every binding lives in desktop.view.jsonc. */
public final class Desktop extends ViewScript {
    private final GitService git = new GitService();
    private final Gson json = new Gson();
    private GitService.Snapshot snapshot;
    private List<GitService.ChangedFile> files = List.of();
    private List<GitService.Commit> history = List.of();
    private final Set<String> included = new LinkedHashSet<>();
    private final List<String> recent = new ArrayList<>();
    private String mode = "changes", panelAction = "open";
    private boolean busy, staged;
    private long diffRevision;
    private static final Path PREFERENCES = Path.of(System.getProperty("user.home"), ".config", "megalodonte-git", "repositories.json");

    @Override public void onMount() {
        run("Abrindo workspace…", () -> {
            List<String> saved = new ArrayList<>();
            if (Files.isRegularFile(PREFERENCES)) {
                try { saved = json.fromJson(Files.readString(PREFERENCES), new TypeToken<List<String>>(){}.getType()); }
                catch (Exception ignored) { /* A damaged preference never prevents starting. */ }
            }
            if (saved == null) saved = new ArrayList<>();
            GitService.Snapshot initial = null;
            String requested = System.getProperty("megalodonte.git.repository", "");
            if (!requested.isBlank()) initial = git.snapshot(git.open(Path.of(requested)));
            else for (String path : saved) {
                try { initial = git.snapshot(git.open(Path.of(path))); break; } catch (Exception ignored) { }
            }
            return new Startup(saved, initial);
        }, result -> {
            recent.addAll(result.recent()); renderRecent();
            if (result.snapshot() != null) apply(result.snapshot());
            else message("Abra um repositório local, clone um remoto ou crie um novo.");
        });
    }
    private record Startup(List<String> recent, GitService.Snapshot snapshot) {}
    private <T> void run(String title, Callable<T> work, java.util.function.Consumer<T> success) {
        if (busy) return;
        busy = true; set("busy", true); message(title);
        background(work, result -> {
            busy = false; set("busy", false);
            try { success.accept(result); } catch (Exception error) { message("Erro: " + error.getMessage()); }
        }, error -> { busy = false; set("busy", false); message("Erro: " + error.getMessage()); });
    }
    private void apply(GitService.Snapshot value) {
        snapshot = value; files = value.changes(); history = value.commits(); diffRevision++;
        Set<String> existing = new HashSet<>(); files.forEach(f -> existing.add(f.path())); included.retainAll(existing);
        set("hasRepo", true); set("repoName", value.repository().root().getFileName().toString());
        set("repoPath", value.repository().root().toString()); set("branch", value.branch());
        set("remote", value.remoteUrl().isBlank() ? "Sem remoto configurado" : value.remoteUrl());
        var sync = value.sync();
        set("pendingPush", sync.ahead() > 0);
        set("pushTitle", sync.ahead() == 1 ? "1 commit aguardando push" : sync.ahead() + " commits aguardando push");
        set("pushHint", "Para " + sync.upstream() + " · comparado ao último fetch");
        set("pushLabel", sync.ahead() > 0 ? "↑  Push (" + sync.ahead() + ")" : "↑  Push");
        set("selectedFile", -1); set("selectedCommit", -1); set("selectedBranch", -1);
        set("diff", ""); set("diffTitle", "Selecione um arquivo para ver as alterações");
        set("branchItems", json.toJson(value.branches().stream().map(b -> Map.of("label", b, "badge", b.equals(value.branch()) ? "ATUAL" : "")).toList()));
        renderFiles(); renderHistory();
        String localStatus = files.isEmpty() ? "Nenhuma alteração local" : files.size() + " arquivo(s) alterado(s) · escolha o que incluir no commit";
        message(localStatus + (sync.ahead() > 0 ? " · " + sync.ahead() + " commit(s) aguardando push" : ""));
        String path = value.repository().root().toString();
        recent.remove(path); recent.addFirst(path); if (recent.size() > 12) recent.removeLast(); renderRecent();
        List<String> saved = List.copyOf(recent);
        background(() -> { Files.createDirectories(PREFERENCES.getParent());
            Path temp = Files.createTempFile(PREFERENCES.getParent(), "repos-", ".tmp");
            try { Files.writeString(temp, json.toJson(saved)); Files.move(temp, PREFERENCES, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            finally { Files.deleteIfExists(temp); } return null;
        }, ignored -> {}, error -> message("Repositório aberto; não foi possível salvar recentes: " + error.getMessage()));
    }
    private void renderRecent() {
        set("repoItems", json.toJson(recent.stream().map(p -> Map.of("label", Path.of(p).getFileName().toString(), "detail", p)).toList()));
    }
    private void renderFiles() {
        set("fileItems", json.toJson(files.stream().map(f -> Map.of("label", (included.contains(f.path()) ? "☑ " : "☐ ") + f.path(),
            "detail", f.conflicted() ? "Conflito · resolva antes de commitar" : f.originalPath() != null && !f.originalPath().isBlank() ? "De " + f.originalPath() : f.staged() ? "No índice" : "Working tree",
            "badge", "" + f.indexStatus() + f.workTreeStatus())).toList()));
        set("changesTitle", "Alterações (" + files.size() + ")"); set("commitLabel", "Commit · " + included.size() + " arquivo(s)");
    }
    private void renderHistory() {
        set("commitItems", json.toJson(history.stream().map(c -> Map.of("label", c.subject(), "detail", c.author() + " · " + c.date(), "badge", c.shortHash())).toList()));
    }
    private GitService.Repository repo() { return snapshot.repository(); }
    private GitService.ChangedFile selectedFile() {
        int i = (Integer) get("selectedFile"); return i >= 0 && i < files.size() ? files.get(i) : null;
    }
    public void chooseRepo() {
        if (busy) return;
        Path chosen = chooseDirectory("Abrir repositório Git", snapshot == null ? Path.of(System.getProperty("user.home")) : repo().root());
        if (chosen != null) openPath(chosen);
    }
    private void openPath(Path path) { included.clear(); run("Lendo repositório…", () -> git.snapshot(git.open(path)), this::apply); }
    public void openRecent() {
        int index = (Integer) get("selectedRepo"); if (index >= 0 && index < recent.size() && !busy) openPath(Path.of(recent.get(index)));
    }
    public void refresh() { if (snapshot != null) run("Atualizando…", () -> git.snapshot(repo()), this::apply); }
    public void changes() { mode = "changes"; set("changesMode", true); set("historyMode", false); set("diff", ""); set("diffTitle", "Selecione um arquivo"); diffRevision++; }
    public void history() { mode = "history"; set("changesMode", false); set("historyMode", true); set("diff", ""); set("diffTitle", "Selecione um commit"); diffRevision++; }
    public void selectFile() {
        var file = selectedFile(); if (file == null || busy) return;
        long revision = ++diffRevision; var repository = repo(); boolean index = staged;
        set("diffTitle", file.path() + (index ? " · índice" : " · working tree"));
        background(() -> git.diff(repository, file, index), diff -> { if (revision == diffRevision) set("diff", diff.isBlank() ? "Nenhuma diferença nesta área. Alterne Working tree / Índice." : diff); },
            error -> { if (revision == diffRevision) message("Erro no diff: " + error.getMessage()); });
    }
    public void workingDiff() { staged = false; selectFile(); }
    public void indexDiff() { staged = true; selectFile(); }
    public void toggleFile() { var file = selectedFile(); if (file != null) { if (!included.remove(file.path())) included.add(file.path()); renderFiles(); } }
    public void selectAll() { included.clear(); files.stream().filter(f -> !f.conflicted()).forEach(f -> included.add(f.path())); renderFiles(); }
    public void selectNone() { included.clear(); renderFiles(); }
    public void selectCommit() {
        int i = (Integer) get("selectedCommit"); if (i < 0 || i >= history.size() || busy) return;
        var commit = history.get(i); var repository = repo(); long revision = ++diffRevision;
        set("diffTitle", commit.shortHash() + " · " + commit.subject());
        background(() -> git.commitDiff(repository, commit.hash()), diff -> { if (revision == diffRevision) set("diff", diff); },
            error -> { if (revision == diffRevision) message("Erro: " + error.getMessage()); });
    }
    public void moreHistory() {
        if (snapshot == null) return;
        int skip = history.size(); run("Carregando histórico…", () -> git.log(repo(), skip, 100), more -> {
            var all = new ArrayList<>(history); all.addAll(more); history = List.copyOf(all); renderHistory(); message(more.isEmpty() ? "Fim do histórico" : history.size() + " commits carregados");
        });
    }
    @Override public void onSubmit() {
        if (snapshot == null || busy) return;
        String summary = text("summary").strip(), body = text("description").strip();
        if (summary.isBlank()) { message("Informe o resumo do commit."); return; }
        if (included.isEmpty()) { message("Selecione os arquivos que deseja incluir."); return; }
        List<String> paths = List.copyOf(included); String message = summary + (body.isBlank() ? "" : "\n\n" + body);
        run("Criando commit…", () -> { git.commit(repo(), paths, message); return git.snapshot(repo()); }, value -> {
            included.clear(); set("summary", ""); set("description", ""); apply(value); message("Commit criado com sucesso.");
        });
    }
    private void operation(String label, IoAction action) {
        if (snapshot == null) return;
        run(label, () -> { action.run(); return git.snapshot(repo()); }, value -> { apply(value); message(label.replace("…", "") + " · concluído"); });
    }
    @FunctionalInterface private interface IoAction { void run() throws Exception; }
    public void stage() { var file = selectedFile(); if (file != null) operation("Adicionando ao índice…", () -> git.stage(repo(), List.of(file.path()))); }
    public void unstage() { var file = selectedFile(); if (file != null) operation("Retirando do índice…", () -> git.unstage(repo(), List.of(file.path()))); }
    public void fetch() { operation("Buscando remotos…", () -> git.fetch(repo())); }
    public void pull() { operation("Recebendo (fast-forward)…", () -> git.pull(repo())); }
    public void push() { operation("Enviando commits…", () -> git.push(repo())); }
    public void branches() { set("branchPanel", !(Boolean) get("branchPanel")); }
    public void branchSelected() { /* Selection is declarative; switching requires the explicit button. */ }
    public void switchBranch() {
        int i = (Integer) get("selectedBranch"); if (snapshot == null || i < 0 || i >= snapshot.branches().size()) return;
        String branch = snapshot.branches().get(i); operation("Trocando branch…", () -> git.switchBranch(repo(), branch));
    }
    private void panel(String action, String title, String hint, boolean second) {
        panelAction = action; set("panelTitle", title); set("panelHint", hint); set("panelValue", ""); set("panelExtra", "");
        set("panelSecond", second); set("panelVisible", true);
    }
    public void clonePanel() { panel("clone", "Clonar repositório", "URL Git ou caminho local; abaixo, pasta de destino nova", true); }
    public void initPanel() { panel("init", "Criar repositório", "Caminho da pasta para o novo repositório", false); }
    public void newBranchPanel() { if (snapshot != null) panel("branch", "Criar e trocar branch", "Nome da nova branch", false); }
    public void stashPanel() { if (snapshot != null) panel("stash", "Guardar alterações no stash", "Descrição do stash (inclui arquivos não rastreados)", false); }
    public void restoreStashPanel() { if (snapshot != null) panel("pop", "Restaurar último stash", "Digite RESTAURAR para aplicar e remover o último stash. Conflitos serão exibidos.", false); }
    public void closePanel() { set("panelVisible", false); }
    public void submitPanel() {
        if (busy) return;
        String value = text("panelValue").strip(), extra = text("panelExtra").strip();
        if (value.isEmpty()) { message("Preencha o campo antes de continuar."); return; }
        try {
            switch (panelAction) {
                case "clone" -> { if (extra.isEmpty()) { message("Informe a pasta de destino nova."); return; }
                    Path destination = Path.of(extra); run("Clonando…", () -> git.snapshot(git.cloneRepository(value, destination)), this::apply); }
                case "init" -> { Path destination = Path.of(value); run("Criando repositório…", () -> git.snapshot(git.init(destination)), this::apply); }
                case "branch" -> operation("Criando branch…", () -> git.createBranch(repo(), value));
                case "stash" -> operation("Guardando alterações…", () -> git.stashSave(repo(), value));
                case "pop" -> { if (!value.equals("RESTAURAR")) { message("Digite RESTAURAR para confirmar."); return; } operation("Restaurando stash…", () -> git.stashPop(repo())); }
                default -> { return; }
            }
            set("panelVisible", false);
        } catch (Exception error) { message("Erro: " + error.getMessage()); }
    }
}
