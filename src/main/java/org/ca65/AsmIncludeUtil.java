package org.ca65;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.AsmDotexpr;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the {@code .include} graph between assembly files.
 *
 * <p>ca65 builds a translation unit by textually pulling in {@code .include}d files, so a symbol
 * defined in an include is visible in any file that (transitively) includes it. These helpers model
 * that relationship in both directions:
 * <ul>
 *     <li>{@link #getIncludedFiles(AsmFile)} &mdash; downward, for resolving references.</li>
 *     <li>{@link #getIncluders(AsmFile)} &mdash; upward, for finding usages / unused definitions.</li>
 * </ul>
 *
 * <p>All walks are cycle-safe (a visited set guards against {@code a includes b includes a}).
 */
public final class AsmIncludeUtil {
    private AsmIncludeUtil() {
    }

    /** The files directly named by {@code .include} / {@code .incbin} directives in {@code file}. */
    public static @NotNull List<AsmFile> getDirectIncludes(@NotNull AsmFile file) {
        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(computeDirectIncludes(file), file, PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static @NotNull List<AsmFile> computeDirectIncludes(@NotNull AsmFile file) {
        List<AsmFile> result = new ArrayList<>();
        VirtualFile dir = includeBaseDir(file);
        AsmDotexpr[] dotexprs = PsiTreeUtil.getChildrenOfType(file, AsmDotexpr.class);
        if (dotexprs == null) {
            return result;
        }
        for (AsmDotexpr dotexpr : dotexprs) {
            if (!isSourceIncludeDirective(dotexpr)) {
                continue; // .incbin pulls in raw binary, not symbols - excluded from the graph
            }
            String path = includePath(dotexpr);
            if (path == null) {
                continue;
            }
            AsmFile target = resolve(file.getProject(), dir, path);
            if (target != null && !target.equals(file)) {
                result.add(target);
            }
        }
        return result;
    }

    /**
     * Every file reachable from {@code file} via {@code .include}, in depth-first order. Does not
     * contain {@code file} itself.
     */
    public static @NotNull Set<AsmFile> getIncludedFiles(@NotNull AsmFile file) {
        Set<AsmFile> visited = new LinkedHashSet<>();
        collectIncludes(file, visited);
        visited.remove(file);
        return visited;
    }

    private static void collectIncludes(@NotNull AsmFile file, @NotNull Set<AsmFile> visited) {
        if (!visited.add(file)) {
            return; // already seen - breaks include cycles
        }
        for (AsmFile included : getDirectIncludes(file)) {
            collectIncludes(included, visited);
        }
    }

    /**
     * Every file that transitively {@code .include}s {@code file}, plus {@code file} itself. This is
     * the set of files in which a symbol defined in {@code file} is visible.
     */
    public static @NotNull Set<AsmFile> getIncluders(@NotNull AsmFile file) {
        Set<AsmFile> result = new LinkedHashSet<>();
        result.add(file);
        for (AsmFile candidate : allAsmFiles(file.getProject())) {
            if (candidate.equals(file)) {
                continue;
            }
            if (getIncludedFiles(candidate).contains(file)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** Search scope spanning {@code file} and every file that transitively includes it. */
    public static @NotNull GlobalSearchScope getIncluderScope(@NotNull AsmFile file) {
        Set<AsmFile> files = getIncluders(file);
        List<VirtualFile> virtualFiles = new ArrayList<>(files.size());
        for (AsmFile asmFile : files) {
            VirtualFile vf = asmFile.getOriginalFile().getVirtualFile();
            if (vf != null) {
                virtualFiles.add(vf);
            }
        }
        if (virtualFiles.isEmpty()) {
            return GlobalSearchScope.fileScope(file);
        }
        return GlobalSearchScope.filesScope(file.getProject(), virtualFiles);
    }

    /** The lower-cased {@code .keyword} that opens this directive, or {@code null}. */
    private static @Nullable String keyword(@NotNull AsmDotexpr dotexpr) {
        ASTNode keyword = dotexpr.getNode().findChildByType(AsmTypes.DOT_KEYWORD);
        return keyword == null ? null : keyword.getText().toLowerCase();
    }

    /** True if {@code dotexpr} is a {@code .include} (an assembly source pulled into the unit). */
    public static boolean isSourceIncludeDirective(@NotNull AsmDotexpr dotexpr) {
        return ".include".equals(keyword(dotexpr));
    }

    /** True if {@code dotexpr} is an {@code .incbin} (a raw binary asset, not a source file). */
    public static boolean isBinaryIncludeDirective(@NotNull AsmDotexpr dotexpr) {
        return ".incbin".equals(keyword(dotexpr));
    }

    /** True for either include directive ({@code .include} or {@code .incbin}). */
    public static boolean isIncludeDirective(@NotNull AsmDotexpr dotexpr) {
        return isSourceIncludeDirective(dotexpr) || isBinaryIncludeDirective(dotexpr);
    }

    /** The unquoted path argument of an include directive, or {@code null} if absent. */
    public static @Nullable String includePath(@NotNull AsmDotexpr dotexpr) {
        ASTNode literal = findStringLiteral(dotexpr.getNode());
        if (literal == null) {
            return null;
        }
        return unquote(literal.getText());
    }

    public static @Nullable ASTNode findStringLiteral(@NotNull ASTNode node) {
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == AsmTypes.STRING_LITERAL) {
                return child;
            }
            ASTNode nested = findStringLiteral(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    public static @NotNull String unquote(@NotNull String text) {
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    /** Resolve {@code path} (as written in an include directive) to an assembly file, or {@code null}. */
    public static @Nullable AsmFile resolve(@NotNull Project project, @Nullable VirtualFile baseDir, @NotNull String path) {
        VirtualFile target = resolveFile(project, baseDir, path);
        if (target == null || target.isDirectory()) {
            return null;
        }
        return PsiManager.getInstance(project).findFile(target) instanceof AsmFile asmFile ? asmFile : null;
    }

    /**
     * Resolve a raw include path to a {@link VirtualFile}, searching the base directory first and then
     * the configured include directories. Used for {@code .include} (filtered to asm files by the
     * caller) and {@code .incbin} (any file). Returns {@code null} if nothing matches.
     */
    public static @Nullable VirtualFile resolveFile(@NotNull Project project, @Nullable VirtualFile baseDir, @NotNull String path) {
        if (baseDir != null) {
            VirtualFile target = baseDir.findFileByRelativePath(path);
            if (target != null) {
                return target;
            }
        }
        for (VirtualFile root : includeRoots(project)) {
            VirtualFile target = root.findFileByRelativePath(path);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    /**
     * Directories that act as additional include roots (ca65 {@code -I} equivalent). Source roots
     * come first &mdash; marking a directory as a <em>Sources Root</em> is the portable, cross-IDE way
     * to add an include path &mdash; with content roots as a last-resort fallback.
     */
    public static @NotNull List<VirtualFile> includeRoots(@NotNull Project project) {
        com.intellij.openapi.roots.ProjectRootManager rootManager =
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project);
        List<VirtualFile> roots = new ArrayList<>();
        for (VirtualFile root : rootManager.getContentSourceRoots()) {
            roots.add(root);
        }
        for (VirtualFile root : rootManager.getContentRoots()) {
            if (!roots.contains(root)) {
                roots.add(root);
            }
        }
        return roots;
    }

    /** Directory that include paths in {@code file} are resolved against (the file's own directory). */
    static @Nullable VirtualFile includeBaseDir(@NotNull AsmFile file) {
        VirtualFile vf = file.getOriginalFile().getVirtualFile();
        return vf == null ? null : vf.getParent();
    }

    private static @NotNull Collection<AsmFile> allAsmFiles(@NotNull Project project) {
        List<AsmFile> result = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile vf : FileTypeIndex.getFiles(AsmFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
            if (psiManager.findFile(vf) instanceof AsmFile asmFile) {
                result.add(asmFile);
            }
        }
        return result;
    }
}
