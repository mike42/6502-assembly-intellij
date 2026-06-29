package org.ca65;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ProcessingContext;
import org.ca65.psi.AsmDotexpr;
import org.ca65.psi.AsmFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Contributes a navigable, renamable file reference (with path completion) to the path argument of
 * {@code .include} / {@code .incbin} directives.
 *
 * <p>The two directives are handled separately:
 * <ul>
 *     <li>{@code .include} resolves/completes <em>assembly source files</em> only.</li>
 *     <li>{@code .incbin} resolves/completes <em>any file</em> (raw binary assets) and is soft, so a
 *     missing (possibly build-generated) asset is not flagged.</li>
 * </ul>
 *
 * <p>Paths resolve against the including file's directory first, then the configured include
 * directories (source roots, then content roots) from {@link AsmIncludeUtil#includeRoots}.
 *
 * <p>The host {@link AsmDotexpr} is a {@link ContributedReferenceHost}, which is what lets these
 * contributed references apply to a custom-language element.
 */
public class AsmIncludeReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(AsmDotexpr.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        AsmDotexpr dotexpr = (AsmDotexpr) element;
                        if (!AsmIncludeUtil.isIncludeDirective(dotexpr)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        ASTNode literal = AsmIncludeUtil.findStringLiteral(dotexpr.getNode());
                        if (literal == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        // Offset of the path within the directive: into the string, past the quote.
                        int startInElement = literal.getStartOffset() - dotexpr.getNode().getStartOffset() + 1;
                        String path = AsmIncludeUtil.unquote(literal.getText());
                        boolean sourceOnly = AsmIncludeUtil.isSourceIncludeDirective(dotexpr);
                        return new IncludeFileReferenceSet(path, dotexpr, startInElement, sourceOnly).getAllReferences();
                    }
                });
    }

    private static final class IncludeFileReferenceSet extends FileReferenceSet {
        private final boolean sourceOnly;

        private IncludeFileReferenceSet(@NotNull String path, @NotNull PsiElement element, int startInElement, boolean sourceOnly) {
            super(path, element, startInElement, null, true);
            this.sourceOnly = sourceOnly;
        }

        @Override
        public boolean isSoft() {
            // Binary assets may be generated at build time, so do not error when missing.
            return !sourceOnly;
        }

        @Override
        public @NotNull Collection<PsiFileSystemItem> computeDefaultContexts() {
            Collection<PsiFileSystemItem> contexts = new ArrayList<>();
            PsiFile containingFile = getContainingFile();
            if (containingFile != null) {
                PsiDirectory dir = containingFile.getOriginalFile().getParent();
                if (dir != null) {
                    contexts.add(dir);
                }
            }
            PsiManager psiManager = PsiManager.getInstance(getElement().getProject());
            for (VirtualFile root : AsmIncludeUtil.includeRoots(getElement().getProject())) {
                PsiDirectory dir = psiManager.findDirectory(root);
                if (dir != null && !contexts.contains(dir)) {
                    contexts.add(dir);
                }
            }
            return contexts;
        }

        @Override
        protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
            if (!sourceOnly) {
                return super.getReferenceCompletionFilter();
            }
            // .include only points at assembly sources, so completion offers directories + asm files.
            return item -> item.isDirectory() || item instanceof AsmFile;
        }
    }
}
