package org.ca65;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.psi.AsmFile;
import org.ca65.psi.impl.AsmIdentifierImpl;

import java.util.List;

/**
 * Cross-file ({@code .include}) reference behaviour: resolution into includes, include-aware unused
 * detection, the include-path file reference, and cycle safety.
 */
public class AsmCrossFileReferenceTest extends BasePlatformTestCase {
    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    /** A symbol defined in an included file resolves from the includer. */
    public void testIncludedSymbolResolves() {
        myFixture.configureByFiles("crossfile/main.asm", "crossfile/constants.inc");
        PsiElement target = resolveIdentifier("PRINT_CHAR");
        assertNotNull("PRINT_CHAR should resolve", target);
        assertEquals("PRINT_CHAR should resolve into the include", "constants.inc",
                target.getContainingFile().getName());
    }

    /** A symbol defined in the file itself still resolves locally. */
    public void testLocalSymbolStillResolves() {
        myFixture.configureByFiles("crossfile/main.asm", "crossfile/constants.inc");
        PsiElement target = resolveIdentifier("FILL_BYTE");
        assertNotNull("FILL_BYTE should resolve", target);
        assertEquals("main.asm", target.getContainingFile().getName());
    }

    /** With the include resolved, none of its symbols are reported as unresolved. */
    public void testNoUnresolvedErrorsAcrossInclude() {
        myFixture.configureByFiles("crossfile/main.asm", "crossfile/constants.inc");
        List<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        for (HighlightInfo error : errors) {
            String description = error.getDescription();
            assertFalse("Unexpected unresolved-symbol error: " + description,
                    description != null && description.contains("not defined"));
        }
    }

    /** The include path string navigates to the included file. */
    public void testIncludePathResolvesToFile() {
        myFixture.configureByFiles("crossfile/main.asm", "crossfile/constants.inc");
        AsmFile main = (AsmFile) myFixture.getFile();
        PsiElement leaf = PsiTreeUtil.collectElements(main, e ->
                e instanceof com.intellij.psi.impl.source.tree.LeafPsiElement
                        && e.getText().equals("\"constants.inc\""))[0];
        PsiReference reference = main.findReferenceAt(
                leaf.getTextRange().getStartOffset() + 1);
        assertNotNull("Include path should have a reference", reference);
        PsiElement resolved = reference.resolve();
        assertTrue("Include path should resolve to a file", resolved instanceof PsiFile);
        assertEquals("constants.inc", ((PsiFile) resolved).getName());
    }

    /** A symbol used only by the includer is not flagged unused; a genuinely unused one is. */
    public void testUnusedDetectionAcrossInclude() {
        myFixture.configureByFiles("crossfile/constants.inc", "crossfile/main.asm");
        List<HighlightInfo> warnings = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING);
        assertFalse("PRINT_CHAR is used by the includer and must not be flagged unused",
                mentions(warnings, "PRINT_CHAR"));
        assertTrue("SPARE_VALUE is referenced nowhere and should be flagged unused",
                mentions(warnings, "SPARE_VALUE"));
    }

    /** A mutually-including pair of files must not hang the include walk. */
    public void testIncludeCycleTerminates() {
        myFixture.configureByFiles("cycle/a.asm", "cycle/b.asm");
        AsmFile a = (AsmFile) myFixture.getFile();
        // Both files are reachable, and the walk returns rather than recursing forever.
        assertEquals(1, AsmIncludeUtil.getIncludedFiles(a).size());
        myFixture.doHighlighting(); // would time out if resolution looped
    }

    private PsiElement resolveIdentifier(String name) {
        AsmFile main = (AsmFile) myFixture.getFile();
        for (AsmIdentifierImpl identifier : PsiTreeUtil.findChildrenOfType(main, AsmIdentifierImpl.class)) {
            if (name.equals(identifier.getName())) {
                PsiReference reference = identifier.getReference();
                return reference == null ? null : reference.resolve();
            }
        }
        fail("No identifier '" + name + "' found in main.asm");
        return null;
    }

    private static boolean mentions(List<HighlightInfo> infos, String symbol) {
        for (HighlightInfo info : infos) {
            if (info.getDescription() != null && info.getDescription().contains(symbol)) {
                return true;
            }
        }
        return false;
    }
}
