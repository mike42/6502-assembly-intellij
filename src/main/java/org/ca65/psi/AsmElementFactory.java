package org.ca65.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;
import org.ca65.AsmFileType;
import org.ca65.psi.impl.AsmLocalLabelRef;

public class AsmElementFactory {
    public static AsmMarker createMarker(Project project, String newName) {
        String text = newName + ":\n";
        final AsmFile file = createFile(project, text);
        return (AsmMarker) file.getFirstChild();
    }

    public static AsmIdentifier createIdentifier(Project project, String newName) {
        String text = "jmp " + newName + "\n";
        final AsmFile file = createFile(project, text);
        // Walk through to identifier (this is quite fragile)
        return (AsmIdentifier) file.getFirstChild().getFirstChild().getNextSibling().getNextSibling().getFirstChild().getFirstChild();
    }

    public static AsmLocalLabelRef createLocalLabelRef(Project project, String newName) {
        String text = "jmp @" + newName + "\n";
        final AsmFile file = createFile(project, text);
        // Walk through to identifier (this is quite fragile)
        return (AsmLocalLabelRef) file.getFirstChild().getFirstChild().getNextSibling().getNextSibling().getFirstChild().getFirstChild();
    }

    public static AsmFile createFile(Project project, String text) {
        String name = "dummy.s";
        return (AsmFile) PsiFileFactory.getInstance(project).createFileFromText(name, AsmFileType.INSTANCE, text);
    }

    public static AsmIdentifierdef createIdentifierDef(Project project, String newName) {
        String text = ".import " + newName + "\n";
        final AsmFile file = createFile(project, text);
        // Walk through to identifier (this is quite fragile)
        return (AsmIdentifierdef) file.getFirstChild().getFirstChild().getNextSibling().getNextSibling();
    }
}
