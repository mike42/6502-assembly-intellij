package org.ca65;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.*;
import org.ca65.psi.impl.AsmPsiImplUtil;
import org.ca65.psi.impl.AsmProcDefMixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Symbol resolution for ca65 sources.
 *
 * <p>Resolution is built around <em>containers</em>: the file, and each {@code .scope}, {@code .proc},
 * {@code .struct}, {@code .union} and {@code .enum} block. A container holds named definitions
 * (labels, constants, named blocks) plus the members spilled from any <em>anonymous</em>
 * struct/union/enum it contains directly. Bare references resolve lexically (innermost container
 * outward, then the {@code .include} graph); {@code A::B::C} references resolve by descending into
 * named child containers.
 */
public class AsmUtil {

    /** True if {@code element} is a container (has its own member namespace). */
    private static boolean isContainer(PsiElement element) {
        return element instanceof AsmFile
                || element instanceof AsmScopeDef
                || element instanceof AsmProcDef
                || element instanceof AsmStructDef
                || element instanceof AsmUnionDef
                || element instanceof AsmEnumDef;
    }

    /** The nearest container enclosing (or equal to) {@code element}. */
    private static PsiElement enclosingContainer(PsiElement element) {
        for (PsiElement cur = element; cur != null; cur = cur.getParent()) {
            if (isContainer(cur)) {
                return cur;
            }
        }
        return null;
    }

    /** The container to descend into for a resolved definition, or null if it is a leaf. */
    private static PsiElement asContainer(PsiNamedElement def) {
        return isContainer(def) ? def : null;
    }

    /**
     * Named definitions directly within {@code container}, including members spilled from anonymous
     * struct/union/enum children. For a struct/union/enum container, its own members are included.
     */
    public static List<PsiNamedElement> membersOf(PsiElement container) {
        List<PsiNamedElement> out = new ArrayList<>();
        if (container == null) {
            return out;
        }

        PsiElement procName = container instanceof AsmProcDef
                ? ((AsmProcDefMixin) container).getNameElement() : null;
        for (AsmMarker marker : getChildren(container, AsmMarker.class)) {
            if (marker != procName) {
                out.add(marker);
            }
        }

        for (AsmImports imports : getChildren(container, AsmImports.class)) {
            out.addAll(imports.getIdentifierdefList());
        }
        for (AsmDefineConstantNumeric constant : getChildren(container, AsmDefineConstantNumeric.class)) {
            out.add(constant.getIdentifierdef());
        }
        for (AsmDefineConstantLabel constant : getChildren(container, AsmDefineConstantLabel.class)) {
            out.add(constant.getIdentifierdef());
        }
        for (AsmScopeDef scope : getChildren(container, AsmScopeDef.class)) {
            if (((PsiNamedElement) scope).getName() != null) {
                out.add((PsiNamedElement) scope);
            }
        }
        for (AsmProcDef proc : getChildren(container, AsmProcDef.class)) {
            if (((PsiNamedElement) proc).getName() != null) {
                out.add((PsiNamedElement) proc);
            }
        }

        for (AsmEnumDef enumDef : getChildren(container, AsmEnumDef.class)) {
            if (enumDef.getIdentifierdef() != null) {
                out.add((PsiNamedElement) enumDef);                 // named: reached via Name::member
            } else {
                for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                    out.add(member.getIdentifierdef());             // anonymous: spilled
                }
            }
        }
        for (AsmStructDef structDef : getChildren(container, AsmStructDef.class)) {
            if (structDef.getIdentifierdef() != null) {
                out.add((PsiNamedElement) structDef);
            } else {
                for (AsmStructMember member : structDef.getStructMemberList()) {
                    out.add((PsiNamedElement) member);
                }
            }
        }
        for (AsmUnionDef unionDef : getChildren(container, AsmUnionDef.class)) {
            if (unionDef.getIdentifierdef() != null) {
                out.add((PsiNamedElement) unionDef);
            } else {
                for (AsmStructMember member : unionDef.getStructMemberList()) {
                    out.add((PsiNamedElement) member);
                }
            }
        }

        // When the container is itself a struct/union/enum, its own members are direct.
        if (container instanceof AsmEnumDef enumDef) {
            for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                out.add(member.getIdentifierdef());
            }
        } else if (container instanceof AsmStructDef structDef) {
            for (AsmStructMember member : structDef.getStructMemberList()) {
                out.add((PsiNamedElement) member);
            }
        } else if (container instanceof AsmUnionDef unionDef) {
            for (AsmStructMember member : unionDef.getStructMemberList()) {
                out.add((PsiNamedElement) member);
            }
        }
        return out;
    }

    /** A direct member named {@code name} within {@code container}, or null. */
    private static PsiNamedElement findInContainer(PsiElement container, String name) {
        if (container == null || name == null) {
            return null;
        }
        for (PsiNamedElement member : membersOf(container)) {
            if (name.equals(member.getName())) {
                return member;
            }
        }
        return null;
    }

    /**
     * Resolve a bare reference {@code name} from {@code refElement}: search the innermost enclosing
     * container, then each enclosing container outward, then the {@code .include} graph.
     */
    public static PsiNamedElement resolveLexical(PsiElement refElement, String name) {
        if (refElement == null || name == null) {
            return null;
        }
        PsiElement container = enclosingContainer(refElement);
        while (container != null) {
            PsiNamedElement hit = findInContainer(container, name);
            if (hit != null) {
                return hit;
            }
            if (container instanceof AsmFile file) {
                for (AsmFile included : AsmIncludeUtil.getIncludedFiles(file)) {
                    hit = findInContainer(included, name);
                    if (hit != null) {
                        return hit;
                    }
                }
                return null;
            }
            container = enclosingContainer(container.getParent());
        }
        return null;
    }

    /**
     * Resolve {@code segments} (a {@code A::B::C} chain, final element included) from
     * {@code refElement}: the first segment is resolved lexically, each subsequent one within the
     * previous segment's container.
     */
    public static PsiNamedElement resolveScoped(PsiElement refElement, List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        PsiElement container = containerForChain(refElement, segments.subList(0, segments.size() - 1));
        return container == null ? null : findInContainer(container, segments.get(segments.size() - 1));
    }

    /** Members visible for completion after a {@code seg0::...::segK::} prefix ({@code segments} = the scopes). */
    public static List<PsiNamedElement> scopeMembersForCompletion(PsiElement refElement, List<String> segments) {
        PsiElement container = containerForChain(refElement, segments);
        return container == null ? new ArrayList<>() : membersOf(container);
    }

    /** Walk a chain of scope names, returning the container they name (or null). */
    private static PsiElement containerForChain(PsiElement refElement, List<String> scopes) {
        if (scopes.isEmpty()) {
            return null;
        }
        PsiElement container = asContainer(resolveLexical(refElement, scopes.get(0)));
        for (int i = 1; i < scopes.size() && container != null; i++) {
            container = asContainer(findInContainer(container, scopes.get(i)));
        }
        return container;
    }

    /** Symbols visible (for completion) at {@code refElement}: enclosing containers then includes. */
    public static List<PsiNamedElement> collectVisibleSymbols(PsiElement refElement) {
        List<PsiNamedElement> result = new ArrayList<>();
        PsiElement container = enclosingContainer(refElement);
        while (container != null) {
            result.addAll(membersOf(container));
            if (container instanceof AsmFile file) {
                for (AsmFile included : AsmIncludeUtil.getIncludedFiles(file)) {
                    result.addAll(membersOf(included));
                }
                break;
            }
            container = enclosingContainer(container.getParent());
        }
        return result;
    }

    /**
     * A file-scoped definition of {@code identifier}: a local ({@code @foo}) label may appear inside
     * a {@code .proc}/{@code .scope}, so markers are searched throughout the file.
     */
    public static PsiNamedElement findDefinitionInFile(AsmFile asmFile, String identifier) {
        if (asmFile == null || identifier == null) {
            return null;
        }
        for (AsmMarker marker : PsiTreeUtil.findChildrenOfType(asmFile, AsmMarker.class)) {
            if (identifier.equals(AsmPsiImplUtil.getLabelName(marker))) {
                return marker;
            }
        }
        return null;
    }

    private static <T extends PsiElement> Collection<T> getChildren(PsiElement parent, Class<T> type) {
        return PsiTreeUtil.getChildrenOfTypeAsList(parent, type);
    }

    public static List<AsmMarker> findLabels(Project project, String label) {
        List<AsmMarker> result = new ArrayList<>();
        Collection<VirtualFile> virtualFiles =
                FileTypeIndex.getFiles(AsmFileType.INSTANCE, GlobalSearchScope.allScope(project));
        for (VirtualFile virtualFile : virtualFiles) {
            AsmFile asmFile = (AsmFile) PsiManager.getInstance(project).findFile(virtualFile);
            if (asmFile != null) {
                AsmMarker[] markers = PsiTreeUtil.getChildrenOfType(asmFile, AsmMarker.class);
                if (markers != null) {
                    for (AsmMarker marker : markers) {
                        if (label.equals(AsmPsiImplUtil.getLabelName(marker))) {
                            result.add(marker);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static List<AsmMarker> findLabels(Project project) {
        List<AsmMarker> result = new ArrayList<>();
        Collection<VirtualFile> virtualFiles =
                FileTypeIndex.getFiles(AsmFileType.INSTANCE, GlobalSearchScope.allScope(project));
        for (VirtualFile virtualFile : virtualFiles) {
            AsmFile asmFile = (AsmFile) PsiManager.getInstance(project).findFile(virtualFile);
            if (asmFile != null) {
                AsmMarker[] markers = PsiTreeUtil.getChildrenOfType(asmFile, AsmMarker.class);
                if (markers != null) {
                    Collections.addAll(result, markers);
                }
            }
        }
        return result;
    }
}
