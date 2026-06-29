package org.ca65;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.helpers.NumericLiteralValue;
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
        NumericLiteralValue lit = NumericLiteralValue.parse(element.getText());
        if (lit == null) return null;
        boolean isHex = lit.getRepresentation() == NumericLiteralValue.Representation.HEX
                     || lit.getRepresentation() == NumericLiteralValue.Representation.ZILOG_HEX;
        boolean isDec = lit.getRepresentation() == NumericLiteralValue.Representation.DECIMAL;
        boolean isBin = lit.getRepresentation() == NumericLiteralValue.Representation.BINARY;
        StringBuilder sb = new StringBuilder("<table>");
        appendRow(sb, "Hexadecimal", lit.toHex(), isHex);
        appendRow(sb, "Decimal",     lit.toDecimal(), isDec);
        appendRow(sb, "Binary",      lit.toBinary(), isBin);
        sb.append("</table>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, String label, String value, boolean isCurrent) {
        sb.append("<tr><td><b>").append(label).append(":</b>&nbsp;</td><td>");
        if (isCurrent) sb.append("<b>");
        sb.append(value);
        if (isCurrent) sb.append("</b>");
        sb.append("</td></tr>");
    }
}
