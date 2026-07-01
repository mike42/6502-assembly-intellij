package org.ca65;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** {@code .scope} and {@code .proc} blocks fold (regression: they used to be plain dotexpr nodes). */
public class AsmFoldingTest extends BasePlatformTestCase {

    public void testScopeAndProcFold() {
        myFixture.configureByText("a.s",
                ".scope SYSTEM\n" +
                "  CTRL = 1\n" +
                "  .proc reset\n" +
                "    rts\n" +
                "  .endproc\n" +
                ".endscope\n" +
                ".proc _main\n" +
                "  rts\n" +
                ".endproc\n");
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        FoldingDescriptor[] regions = new AsmFoldingBuilder()
                .buildFoldRegions(myFixture.getFile(), document, false);

        // Expect a fold spanning the outer scope, the nested proc, and the top-level proc.
        assertTrue("outer .scope should fold", spans(regions, document, 0, 5));
        assertTrue("nested .proc should fold", spans(regions, document, 2, 4));
        assertTrue("top-level .proc should fold", spans(regions, document, 6, 8));
    }

    private static boolean spans(FoldingDescriptor[] regions, Document doc, int startLine, int endLine) {
        for (FoldingDescriptor d : regions) {
            int s = doc.getLineNumber(d.getRange().getStartOffset());
            int e = doc.getLineNumber(d.getRange().getEndOffset());
            if (s == startLine && e == endLine) {
                return true;
            }
        }
        return false;
    }
}
