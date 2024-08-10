package io.jafar.parser.api.types;

import io.jafar.parser.api.JfrField;
import io.jafar.parser.api.JfrIgnore;
import io.jafar.parser.api.JfrType;

@JfrType("java.lang.Class")
public interface JFRClass {
    JFRSymbol name();
    @JfrField("package") JFRPackage pkg();
    int modifiers();
    boolean hidden();

    @JfrIgnore
    default String tostring() {
        StringBuilder sb = new StringBuilder();
        JFRPackage pkg = pkg();
        if (pkg != null) {
            sb.append(pkg.string()).append(".");
        }
        sb.append(name().string());
        return sb.toString();
    }
}
