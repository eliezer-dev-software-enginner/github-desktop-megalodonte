package megalodonte.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {
    @TempDir Path temporary;
    GitService git = new GitService();
    GitService.Repository repository(String name) throws Exception {
        var repo = git.init(temporary.resolve(name));
        command(repo.root(), "config", "user.name", "Test User");
        command(repo.root(), "config", "user.email", "test@example.invalid");
        return repo;
    }
    static String command(Path cwd, String... arguments) throws Exception {
        List<String> args = new ArrayList<>(List.of("git")); args.addAll(List.of(arguments));
        Process process = new ProcessBuilder(args).directory(cwd.toFile()).redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), result); return result.strip();
    }
    void write(GitService.Repository repo, String name, String text) throws Exception { Files.writeString(repo.root().resolve(name), text); }
    @Test void unbornUnicodeLiteralPathsAndCanonicalOpen() throws Exception {
        var repo = repository("first");
        write(repo, "Olá espaço [x].txt", "hello\n");
        var snapshot = git.snapshot(repo);
        assertTrue(snapshot.commits().isEmpty());
        assertFalse(snapshot.sync().tracked());
        assertEquals("Olá espaço [x].txt", snapshot.changes().getFirst().path());
        assertTrue(snapshot.changes().getFirst().untracked());
        assertTrue(git.diff(repo, snapshot.changes().getFirst(), false).contains("hello"));
        git.stage(repo, List.of("Olá espaço [x].txt"));
        git.unstage(repo, List.of("Olá espaço [x].txt"));
        assertTrue(git.status(repo).getFirst().untracked());
        git.commit(repo, List.of("Olá espaço [x].txt"), "Initial");
        Files.createDirectory(repo.root().resolve("sub"));
        assertEquals(repo, git.open(repo.root().resolve("sub")));
        assertEquals("Initial", git.log(repo, 0, 100).getFirst().subject());
    }
    @Test void selectedCommitPreservesOtherStagedContentAndRename() throws Exception {
        var repo = repository("selection");
        write(repo, "a.txt", "a\n"); write(repo, "b.txt", "b\n");
        git.commit(repo, List.of("a.txt", "b.txt"), "Initial");
        write(repo, "a.txt", "changed a\n"); write(repo, "b.txt", "changed b\n");
        git.stage(repo, List.of("b.txt"));
        git.commit(repo, List.of("a.txt"), "Only A");
        assertEquals("b", command(repo.root(), "show", "HEAD:b.txt"));
        assertEquals("changed b", command(repo.root(), "show", ":b.txt"));
        assertEquals(1, git.status(repo).size());
        assertTrue(git.status(repo).getFirst().staged());
        command(repo.root(), "mv", "a.txt", "novo espaço á.txt");
        var rename = git.status(repo).stream().filter(f -> f.originalPath() != null).findFirst().orElseThrow();
        assertEquals("a.txt", rename.originalPath()); assertEquals("novo espaço á.txt", rename.path());
        git.commit(repo, List.of(rename.path()), "Rename");
        assertEquals("changed b", command(repo.root(), "show", ":b.txt"));
        assertEquals(1, git.log(repo, 1, 1).size());
    }
    @Test void branchStashAndLocalRemoteRoundTrip() throws Exception {
        var source = repository("source"); write(source, "one", "one\n"); git.commit(source, List.of("one"), "Initial");
        String original = git.snapshot(source).branch();
        git.createBranch(source, "feature/demo"); assertEquals("feature/demo", git.snapshot(source).branch());
        git.switchBranch(source, original);
        write(source, "untracked", "stash content"); git.stashSave(source, "test stash"); assertTrue(git.status(source).isEmpty()); git.stashPop(source); assertEquals(1, git.status(source).size()); Files.delete(source.root().resolve("untracked"));
        Path bare = temporary.resolve("remote.git"); command(temporary, "init", "--bare", bare.toString());
        command(source.root(), "remote", "add", "origin", bare.toString()); command(source.root(), "push", "-u", "origin", original);
        command(bare, "symbolic-ref", "HEAD", "refs/heads/" + original);
        var clone = git.cloneRepository(bare.toString(), temporary.resolve("clone"));
        assertEquals(1, git.log(clone, 0, 100).size());
        write(source, "two", "two\n"); git.commit(source, List.of("two"), "Second"); git.push(source); git.fetch(clone); git.pull(clone);
        assertEquals(2, git.log(clone, 0, 100).size()); assertEquals("two\n", Files.readString(clone.root().resolve("two")));
        assertFalse(git.snapshot(clone).remoteUrl().isBlank());
        assertEquals(0, git.snapshot(clone).sync().ahead());
        write(clone, "local", "local\n"); git.commit(clone, List.of("local"), "Local only");
        var pending = git.snapshot(clone).sync();
        assertEquals(1, pending.ahead()); assertEquals(0, pending.behind());
        assertFalse(pending.upstream().isBlank());
        write(source, "remote", "remote\n"); git.commit(source, List.of("remote"), "Remote only"); git.push(source); git.fetch(clone);
        var diverged = git.snapshot(clone).sync();
        assertEquals(1, diverged.ahead()); assertEquals(1, diverged.behind());
    }
    @Test void stagedAndWorkingDiffAndConflict() throws Exception {
        var repo = repository("conflict"); write(repo, "same.txt", "base\n"); git.commit(repo, List.of("same.txt"), "Base");
        String original = git.snapshot(repo).branch();
        git.createBranch(repo, "other"); write(repo, "same.txt", "other\n"); git.commit(repo, List.of("same.txt"), "Other");
        git.switchBranch(repo, original); write(repo, "same.txt", "main\n"); git.stage(repo, List.of("same.txt"));
        write(repo, "same.txt", "working\n");
        var changed = git.status(repo).getFirst(); assertTrue(changed.staged()); assertTrue(changed.unstaged());
        assertTrue(git.diff(repo, changed, true).contains("+main")); assertTrue(git.diff(repo, changed, false).contains("+working"));
        git.commit(repo, List.of("same.txt"), "Main");
        Process process = new ProcessBuilder("git", "merge", "other").directory(repo.root().toFile()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes(); assertEquals(1, process.waitFor());
        assertTrue(git.status(repo).getFirst().conflicted());
    }
    @Test void rejectOutsidePathsAndInvalidBranch() throws Exception {
        var repo = repository("invalid");
        assertThrows(java.io.IOException.class, () -> git.stage(repo, List.of("../outside")));
        assertThrows(java.io.IOException.class, () -> git.createBranch(repo, "--bad"));
    }
}
