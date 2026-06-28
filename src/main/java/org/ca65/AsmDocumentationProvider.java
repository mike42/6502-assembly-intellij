package org.ca65;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                              @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int targetOffset) {
        if (contextElement == null) return null;
        return PsiTreeUtil.getParentOfType(contextElement, AsmNumericLiteral.class, false);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (!(element instanceof AsmNumericLiteral)) return null;
        String text = element.getText();
        int value;
        try {
            value = parseNumericLiteral(text);
        } catch (NumberFormatException e) {
            return null;
        }
        StringBuilder sb = new StringBuilder("<table>");
        appendRow(sb, "Hexadecimal", toHex(value), text.startsWith("$"));
        appendRow(sb, "Decimal", Integer.toString(value), !text.startsWith("$") && !text.startsWith("%"));
        appendRow(sb, "Binary", toBinary(value), text.startsWith("%"));
        sb.append("</table>");
        return sb.toString();
    }

    private static int parseNumericLiteral(String text) {
        String digits = text.replace("_", "");
        if (digits.startsWith("$")) {
            return Integer.parseInt(digits.substring(1), 16);
        } else if (digits.startsWith("%")) {
            return Integer.parseInt(digits.substring(1), 2);
        } else {
            return Integer.parseInt(digits, 10);
        }
    }

    private static String toHex(int value) {
        String raw = Integer.toHexString(value);
        if (raw.length() % 2 == 1) raw = "0" + raw;
        return "$" + raw;
    }

    private static String toBinary(int value) {
        String raw = Integer.toBinaryString(value);
        int remainder = raw.length() % 8;
        if (remainder != 0) raw = "0".repeat(8 - remainder) + raw;
        return "%" + raw;
    }

    private static void appendRow(StringBuilder sb, String label, String value, boolean isCurrent) {
        sb.append("<tr><td><b>").append(label).append(":</b>&nbsp;</td><td>");
        if (isCurrent) sb.append("<b>");
        sb.append(value);
        if (isCurrent) sb.append("</b>");
        sb.append("</td></tr>");
    }
}
