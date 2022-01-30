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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AsmUtil {
    public static PsiNamedElement findDefinition(AsmFile asmFile, String identifier) {
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
