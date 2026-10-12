package megalodonte.git;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Git CLI boundary. All methods block and must run off the UI thread. */
public final class GitService {
    private static final int OUTPUT_LIMIT = 8 * 1024 * 1024;
    private static final int DIFF_LIMIT = 256 * 1024;
    private final Duration timeout;
    public GitService() { this(Duration.ofSeconds(90)); }
    public GitService(Duration timeout) { this.timeout = Objects.requireNonNull(timeout); }
    public record Repository(Path root) {}
    public record ChangedFile(String path, String originalPath, char indexStatus, char workTreeStatus) {
        public boolean untracked() { return indexStatus == '?' && workTreeStatus == '?'; }
        public boolean staged() { return !untracked() && indexStatus != ' ' && indexStatus != '!'; }
        public boolean unstaged() { return untracked() || workTreeStatus != ' '; }
        public boolean conflicted() { return indexStatus == 'U' || workTreeStatus == 'U' || (indexStatus == 'A' && workTreeStatus == 'A') || (indexStatus == 'D' && workTreeStatus == 'D'); }
    }
    public record Commit(String hash, String shortHash, String author, String date, String subject) {}
    public record Snapshot(Repository repository, String branch, List<String> branches, List<ChangedFile> changes, List<Commit> commits, String remoteUrl) {}
    public Repository open(Path path) throws IOException {
        Path directory = path.toRealPath();
        String root = run(directory, Map.of(), "rev-parse", "--show-toplevel").strip();
        return new Repository(Path.of(root).toRealPath());
    }
    public Repository init(Path path) throws IOException {
        Files.createDirectories(path); run(path, Map.of(), "init"); return open(path);
    }
    public Repository cloneRepository(String url, Path destination) throws IOException {
        if (url == null || url.isBlank() || url.startsWith("-")) throw new IOException("Informe uma origem Git válida.");
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        run(absolute.getParent(), Map.of(), "clone", "--", url, absolute.toString());
        return open(absolute);
    }
    public Snapshot snapshot(Repository repo) throws IOException {
        String branch = optional(repo, "symbolic-ref", "--quiet", "--short", "HEAD");
        if (branch.isBlank()) branch = "HEAD destacado " + optional(repo, "rev-parse", "--short", "HEAD");
        String refs = git(repo, "for-each-ref", "--format=%(refname:short)", "refs/heads/");
        List<String> branches = refs.lines().filter(s -> !s.isBlank()).toList();
        return new Snapshot(repo, branch.strip(), branches, status(repo), log(repo, 0, 100), redact(optional(repo, "remote", "get-url", "origin").strip()));
    }
    public List<ChangedFile> status(Repository repo) throws IOException {
        String raw = git(repo, "status", "--porcelain=v1", "-z", "--untracked-files=all");
        String[] entries = raw.split("\u0000", -1);
        List<ChangedFile> result = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i]; if (entry.isEmpty()) continue;
            if (entry.length() < 4) throw new IOException("Resposta de status Git inválida.");
            char x = entry.charAt(0), y = entry.charAt(1);
            String original = null;
            if (x == 'R' || x == 'C' || y == 'R' || y == 'C') {
                if (++i >= entries.length) throw new IOException("Rename Git incompleto.");
                original = entries[i];
            }
            result.add(new ChangedFile(entry.substring(3), original, x, y));
        }
        return List.copyOf(result);
    }
    public List<Commit> log(Repository repo, int skip, int limit) throws IOException {
        if (optional(repo, "rev-parse", "--verify", "HEAD").isBlank()) return List.of();
        String raw = git(repo, "log", "--skip=" + Math.max(0, skip), "-n", Integer.toString(Math.clamp(limit, 1, 100)), "--format=%H%x00%h%x00%an%x00%aI%x00%s%x00");
        String[] parts = raw.split("\u0000", -1); List<Commit> result = new ArrayList<>();
        for (int i = 0; i + 4 < parts.length; i += 5) result.add(new Commit(parts[i].strip(), parts[i+1], parts[i+2], parts[i+3], parts[i+4]));
        return List.copyOf(result);
    }
    public String diff(Repository repo, ChangedFile file, boolean staged) throws IOException {
        validatePaths(repo, List.of(file.path()));
        if (file.untracked()) {
            Path path = repo.root().resolve(file.path());
            if (Files.isSymbolicLink(path)) return "Link simbólico → " + Files.readSymbolicLink(path);
            try (InputStream input = Files.newInputStream(path)) {
                byte[] bytes = input.readNBytes(DIFF_LIMIT + 1);
                for (byte b : bytes) if (b == 0) return "Arquivo binário (novo).";
                return "Arquivo novo: " + file.path() + "\n\n" + new String(bytes, 0, Math.min(bytes.length, DIFF_LIMIT), StandardCharsets.UTF_8) + (bytes.length > DIFF_LIMIT ? "\n[Prévia truncada]" : "");
            }
        }
        List<String> args = new ArrayList<>(List.of("diff", "--no-ext-diff", "--no-textconv", "--no-color"));
        if (staged) args.add("--cached"); args.add("--"); args.add(file.path());
        if (file.originalPath() != null) args.add(file.originalPath());
        String value = git(repo, args.toArray(String[]::new));
        return value.length() <= DIFF_LIMIT ? value : value.substring(0, DIFF_LIMIT) + "\n[Prévia truncada]";
    }
    public String commitDiff(Repository repo, String hash) throws IOException {
        if (hash == null || !hash.matches("[0-9a-fA-F]{4,64}")) throw new IOException("Identificador de commit inválido.");
        String value = git(repo, "show", "--no-ext-diff", "--no-textconv", "--no-color", "--format=fuller", "--stat", "--patch", hash, "--");
        return value.length() <= DIFF_LIMIT ? value : value.substring(0, DIFF_LIMIT) + "\n[Prévia truncada]";
    }
    public void stage(Repository repo, List<String> paths) throws IOException { pathCommand(repo, Map.of(), List.of("add", "-A", "--"), paths); }
    public void unstage(Repository repo, List<String> paths) throws IOException {
        if (optional(repo, "rev-parse", "--verify", "HEAD").isBlank()) pathCommand(repo, Map.of(), List.of("rm", "--cached", "-r", "--ignore-unmatch", "--"), paths);
        else pathCommand(repo, Map.of(), List.of("reset", "-q", "HEAD", "--"), paths);
    }
    /** Commits only selected working-tree paths; unrelated entries in the user's index stay staged. */
    public void commit(Repository repo, List<String> paths, String message) throws IOException {
        if (message == null || message.isBlank()) throw new IOException("Escreva a mensagem do commit.");
        validatePaths(repo, paths);
        LinkedHashSet<String> expanded = new LinkedHashSet<>(paths);
        for (ChangedFile changed : status(repo)) if (expanded.contains(changed.path()) && changed.originalPath() != null) expanded.add(changed.originalPath());
        paths = List.copyOf(expanded);
        Path index = Files.createTempFile("megalodonte-git-index-", ".tmp"); Files.delete(index);
        Map<String,String> env = Map.of("GIT_INDEX_FILE", index.toAbsolutePath().toString());
        try {
            if (optional(repo, "rev-parse", "--verify", "HEAD").isBlank()) run(repo.root(), env, "read-tree", "--empty");
            else run(repo.root(), env, "read-tree", "HEAD");
            pathCommand(repo, env, List.of("add", "-A", "--"), paths);
            run(repo.root(), env, "commit", "-m", message);
            pathCommand(repo, Map.of(), List.of("reset", "-q", "HEAD", "--"), paths);
        } finally { Files.deleteIfExists(index); Files.deleteIfExists(Path.of(index + ".lock")); }
    }
    public void createBranch(Repository repo, String name) throws IOException { checkBranch(repo, name); git(repo, "switch", "-c", name); }
    public void switchBranch(Repository repo, String name) throws IOException { checkBranch(repo, name); git(repo, "switch", "--", name); }
    private void checkBranch(Repository repo, String name) throws IOException {
        if (name == null || name.isBlank() || name.startsWith("-")) throw new IOException("Nome de branch inválido.");
        git(repo, "check-ref-format", "--branch", name);
    }
    public void fetch(Repository repo) throws IOException { git(repo, "fetch", "--all", "--prune"); }
    public void pull(Repository repo) throws IOException { git(repo, "pull", "--ff-only"); }
    public void push(Repository repo) throws IOException { git(repo, "push"); }
    public void stashSave(Repository repo, String message) throws IOException { git(repo, "stash", "push", "--include-untracked", "-m", message == null ? "Megalodonte Git" : message); }
    public void stashPop(Repository repo) throws IOException { git(repo, "stash", "pop"); }
    private void pathCommand(Repository repo, Map<String,String> env, List<String> prefix, List<String> paths) throws IOException {
        validatePaths(repo, paths); List<String> args = new ArrayList<>(prefix); args.addAll(paths); run(repo.root(), env, args.toArray(String[]::new));
    }
    private void validatePaths(Repository repo, List<String> paths) throws IOException {
        if (paths == null || paths.isEmpty()) throw new IOException("Selecione pelo menos um arquivo.");
        for (String path : paths) {
            if (path == null || path.isEmpty() || Path.of(path).isAbsolute() || !repo.root().resolve(path).normalize().startsWith(repo.root())) throw new IOException("Caminho fora do repositório.");
        }
    }
    private String git(Repository repo, String... args) throws IOException { return run(repo.root(), Map.of(), args); }
    private String optional(Repository repo, String... args) throws IOException {
        try { return git(repo, args); } catch (GitException e) { return ""; }
    }
    public static final class GitException extends IOException {
        public final int exitCode;
        GitException(int exitCode, String message) { super(message); this.exitCode = exitCode; }
    }
    private record Captured(byte[] bytes, boolean truncated) {}
    private Captured drain(InputStream stream) throws IOException {
        try (stream; ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count; boolean truncated = false;
            while ((count = stream.read(buffer)) >= 0) {
                int remaining = OUTPUT_LIMIT - result.size();
                if (count > remaining) truncated = true;
                if (remaining > 0) result.write(buffer, 0, Math.min(count, remaining));
            }
            return new Captured(result.toByteArray(), truncated);
        }
    }
    private String run(Path cwd, Map<String,String> env, String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of("git"));
        // Git stash internally constructs glob pathspecs for untracked cleanup.
        if (args.length == 0 || !args[0].equals("stash")) command.add("--literal-pathspecs");
        command.addAll(List.of("-c", "core.quotePath=false", "-c", "color.ui=false", "-c", "protocol.allow=never", "-c", "protocol.https.allow=always", "-c", "protocol.ssh.allow=always", "-c", "protocol.git.allow=always", "-c", "protocol.file.allow=always"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0"); builder.environment().put("GIT_PAGER", "cat"); builder.environment().putAll(env);
        Process process = builder.start(); process.getOutputStream().close();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Captured> output = executor.submit(() -> drain(process.getInputStream()));
            Future<Captured> errors = executor.submit(() -> drain(process.getErrorStream()));
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                    throw new IOException("Git excedeu o limite de " + timeout.toSeconds() + " segundos.");
                }
                Captured out = output.get(5, TimeUnit.SECONDS), err = errors.get(5, TimeUnit.SECONDS);
                String text = new String(out.bytes(), StandardCharsets.UTF_8);
                if (process.exitValue() != 0) {
                    String detail = new String(err.bytes(), StandardCharsets.UTF_8).strip();
                    if (detail.isEmpty()) detail = text.strip();
                    if (detail.length() > 4000) detail = detail.substring(0, 4000);
                    throw new GitException(process.exitValue(), redact(detail));
                }
                if (out.truncated() && !(args[0].equals("diff") || args[0].equals("show"))) throw new IOException("Saída Git excedeu 8 MiB; reduza o escopo da operação.");
                return text;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); throw new IOException("Operação Git interrompida.", e); }
            catch (ExecutionException | TimeoutException e) { process.destroyForcibly(); throw new IOException("Não foi possível ler a resposta Git.", e); }
        } finally {
            process.getInputStream().close(); process.getErrorStream().close(); executor.shutdownNow();
        }
    }
    private static String redact(String text) {
        return text.replaceAll("(?i)(https?://)[^\\s/@]+(?::[^\\s/@]*)?@", "$1[credencial]@");
    }
}
