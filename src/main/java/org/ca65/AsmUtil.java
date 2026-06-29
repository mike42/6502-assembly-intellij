package org.ca65;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.*;
import org.ca65.psi.impl.AsmPsiImplUtil;
import org.ca65.psi.impl.AsmStructMemberMixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AsmUtil {
    /**
     * Find the definition of {@code identifier} visible from {@code asmFile}, following the
     * {@code .include} graph. The current file is searched first, then each transitively included
     * file (a symbol defined in an include is visible to the includer).
     */
    public static PsiNamedElement findDefinition(AsmFile asmFile, String identifier) {
        if (asmFile == null || identifier == null) {
            return null;
        }
        PsiNamedElement local = findDefinitionInFile(asmFile, identifier);
        if (local != null) {
            return local;
        }
        for (AsmFile included : AsmIncludeUtil.getIncludedFiles(asmFile)) {
            PsiNamedElement definition = findDefinitionInFile(included, identifier);
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    /** Find a definition of {@code identifier} declared directly within {@code asmFile}. */
    public static PsiNamedElement findDefinitionInFile(AsmFile asmFile, String identifier) {
        if(asmFile == null) {
            return null;
        }
        // Defined labels
        AsmMarker[] markers = PsiTreeUtil.getChildrenOfType(asmFile, AsmMarker.class);
        if (markers != null) {
            for (AsmMarker marker : markers) {
                if (identifier.equals(AsmPsiImplUtil.getLabelName(marker))) {
                    return marker;
                }
            }
        }
        // Imported values. For reasons unknown, getChildrenOfType(asmFile, AsmIdentifierdef.class) is always null, but this works.
        AsmImports[] importStatements = PsiTreeUtil.getChildrenOfType(asmFile, AsmImports.class);
        if (importStatements != null) {
            for (AsmImports importStatement : importStatements) {
                for(AsmIdentifierdef identifierdef : importStatement.getIdentifierdefList()) {
                    if (identifier.equals(AsmPsiImplUtil.getLabelName(identifierdef))) {
                        return identifierdef;
                    }
                }
            }
        }
        // Numeric constants
        AsmDefineConstantNumeric[] numericConstantList = PsiTreeUtil.getChildrenOfType(asmFile, AsmDefineConstantNumeric.class);
        if (numericConstantList != null) {
            for (AsmDefineConstantNumeric numericConstant : numericConstantList) {
                AsmIdentifierdef identifierDef = numericConstant.getIdentifierdef();
                if (identifier.equals(AsmPsiImplUtil.getLabelName(identifierDef))) {
                    return identifierDef;
                }
            }
        }
        // label constants
        AsmDefineConstantLabel[] labelConstantList = PsiTreeUtil.getChildrenOfType(asmFile, AsmDefineConstantLabel.class);
        if (labelConstantList != null) {
            for (AsmDefineConstantLabel labelConstant : labelConstantList) {
                AsmIdentifierdef identifierDef = labelConstant.getIdentifierdef();
                if (identifier.equals(AsmPsiImplUtil.getLabelName(identifierDef))) {
                    return identifierDef;
                }
            }
        }
        // enum names and (for unnamed enums) their members
        AsmEnumDef[] enumDefs = PsiTreeUtil.getChildrenOfType(asmFile, AsmEnumDef.class);
        if (enumDefs != null) {
            for (AsmEnumDef enumDef : enumDefs) {
                AsmIdentifierdef nameDef = enumDef.getIdentifierdef();
                if (nameDef != null && identifier.equals(AsmPsiImplUtil.getLabelName(nameDef))) {
                    return nameDef;
                }
                // unnamed enum members spill into the enclosing scope
                if (nameDef == null) {
                    for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                        AsmIdentifierdef memberDef = member.getIdentifierdef();
                        if (identifier.equals(AsmPsiImplUtil.getLabelName(memberDef))) {
                            return memberDef;
                        }
                    }
                }
            }
        }
        // struct and union names
        AsmStructDef[] structDefs = PsiTreeUtil.getChildrenOfType(asmFile, AsmStructDef.class);
        if (structDefs != null) {
            for (AsmStructDef structDef : structDefs) {
                AsmIdentifierdef nameDef = structDef.getIdentifierdef();
                if (nameDef != null && identifier.equals(AsmPsiImplUtil.getLabelName(nameDef))) {
                    return nameDef;
                }
            }
        }
        AsmUnionDef[] unionDefs = PsiTreeUtil.getChildrenOfType(asmFile, AsmUnionDef.class);
        if (unionDefs != null) {
            for (AsmUnionDef unionDef : unionDefs) {
                AsmIdentifierdef nameDef = unionDef.getIdentifierdef();
                if (nameDef != null && identifier.equals(AsmPsiImplUtil.getLabelName(nameDef))) {
                    return nameDef;
                }
            }
        }
        return null;
    }

    /**
     * Resolve a scoped member reference: given a struct/union/enum name and a member name,
     * return the PSI element for the member definition, searching the include graph.
     */
    public static PsiNamedElement findScopedMember(AsmFile asmFile, String scopeName, String memberName) {
        if (asmFile == null || scopeName == null || memberName == null) return null;
        PsiNamedElement local = findScopedMemberInFile(asmFile, scopeName, memberName);
        if (local != null) return local;
        for (AsmFile included : AsmIncludeUtil.getIncludedFiles(asmFile)) {
            PsiNamedElement def = findScopedMemberInFile(included, scopeName, memberName);
            if (def != null) return def;
        }
        return null;
    }

    private static PsiNamedElement findScopedMemberInFile(AsmFile asmFile, String scopeName, String memberName) {
        // struct members
        AsmStructDef[] structDefs = PsiTreeUtil.getChildrenOfType(asmFile, AsmStructDef.class);
        if (structDefs != null) {
            for (AsmStructDef structDef : structDefs) {
                AsmIdentifierdef nameDef = structDef.getIdentifierdef();
                if (nameDef != null && scopeName.equals(AsmPsiImplUtil.getLabelName(nameDef))) {
                    for (AsmStructMember member : structDef.getStructMemberList()) {
                        if (memberName.equals(((AsmStructMemberMixin) member).getName())) {
                            return (PsiNamedElement) member;
                        }
                    }
                }
            }
        }
        // union members
        AsmUnionDef[] unionDefs = PsiTreeUtil.getChildrenOfType(asmFile, AsmUnionDef.class);
        if (unionDefs != null) {
            for (AsmUnionDef unionDef : unionDefs) {
                AsmIdentifierdef nameDef = unionDef.getIdentifierdef();
                if (nameDef != null && scopeName.equals(AsmPsiImplUtil.getLabelName(nameDef))) {
                    for (AsmStructMember member : unionDef.getStructMemberList()) {
                        if (memberName.equals(((AsmStructMemberMixin) member).getName())) {
                            return (PsiNamedElement) member;
                        }
                    }
                }
            }
        }
        // enum members for named enums
        AsmEnumDef[] enumDefs = PsiTreeUtil.getChildrenOfType(asmFile, AsmEnumDef.class);
        if (enumDefs != null) {
            for (AsmEnumDef enumDef : enumDefs) {
                AsmIdentifierdef nameDef = enumDef.getIdentifierdef();
                if (nameDef != null && scopeName.equals(AsmPsiImplUtil.getLabelName(nameDef))) {
                    for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                        if (memberName.equals(AsmPsiImplUtil.getLabelName(member.getIdentifierdef()))) {
                            return member.getIdentifierdef();
                        }
                    }
                }
            }
        }
        return null;
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
