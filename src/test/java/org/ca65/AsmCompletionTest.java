package org.ca65;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * Completion: control directives after {@code .}, scoped members after {@code ::}, the {@code ::}
 * auto-append when completing a scope name, and the broadened symbol set.
 */
public class AsmCompletionTest extends BasePlatformTestCase {

    public void testDirectiveCompletionOnBareDot() {
        myFixture.configureByText("a.s", ".<caret>");
        myFixture.complete(CompletionType.BASIC);
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertContainsElements(items, ".proc", ".byte", ".enum", ".segment", ".macro");
    }

    public void testDirectiveCompletionWithPrefixInserts() {
        myFixture.configureByText("a.s", "  .by<caret>");
        myFixture.complete(CompletionType.BASIC);
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertContainsElements(items, ".byte", ".byt");
        assertDoesntContain(items, ".proc");
    }

    public void testDirectiveCompletionAutoInsertsSingleMatch() {
        // ".zer" uniquely identifies ".zeropage"
        myFixture.configureByText("a.s", "  .zer<caret>");
        myFixture.complete(CompletionType.BASIC);
        myFixture.checkResult("  .zeropage<caret>");
    }

    public void testScopedMemberCompletionOnlyOffersThatScope() {
        myFixture.configureByText("a.s",
                ".enum SIZE\n" +
                "  SMALL\n" +
                "  MEDIUM\n" +
                "  LARGE\n" +
                ".endenum\n" +
                ".enum COLOR\n" +
                "  RED\n" +
                "  GREEN\n" +
                ".endenum\n" +
                "lda #SIZE::<caret>\n");
        myFixture.complete(CompletionType.BASIC);
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertSameElements(items, "SMALL", "MEDIUM", "LARGE");
        assertDoesntContain(items, "RED", "GREEN");
    }

    public void testCompletingScopeNameAppendsScopeAccess() {
        myFixture.configureByText("a.s",
                ".enum SIZE\n" +
                "  SMALL\n" +
                ".endenum\n" +
                "lda #SIZE<caret>\n");
        myFixture.complete(CompletionType.BASIC);
        // Only SIZE matches, so it is inserted and the handler appends "::".
        myFixture.checkResult(
                ".enum SIZE\n" +
                "  SMALL\n" +
                ".endenum\n" +
                "lda #SIZE::<caret>\n");
    }

    public void testSymbolCompletionIncludesConstants() {
        myFixture.configureByText("a.s",
                "MY_CONST = $10\n" +
                "lda #<caret>\n");
        myFixture.complete(CompletionType.BASIC);
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertContainsElements(items, "MY_CONST");
    }

    /** Every symbol offered after a mnemonic has an icon — labels and constants alike. */
    public void testSymbolCompletionItemsHaveIcons() {
        myFixture.configureByText("a.s",
                "mylabel:\n" +
                "MY_CONST = $10\n" +
                "lda <caret>\n");
        LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
        assertNotNull(elements);
        for (LookupElement element : elements) {
            LookupElementPresentation presentation = new LookupElementPresentation();
            element.renderElement(presentation);
            assertNotNull("'" + element.getLookupString() + "' should have an icon",
                    presentation.getIcon());
        }
    }
}
