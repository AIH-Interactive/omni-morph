package org.figuramc.figura.model.ysm;

import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.util.List;

public final class YsmMolangFunctionParser {
    private YsmMolangFunctionParser() {
    }

    public static ParsedFunction parse(String path, String source, ObjectBinding bindings) throws Exception {
        YsmMolangFunctionName name = YsmMolangFunctionName.parse(path);
        List<Expression> expressions = MolangEngine.fromCustomBinding(bindings).parse(source);
        return new ParsedFunction(name, expressions);
    }

    public record ParsedFunction(YsmMolangFunctionName name, List<Expression> expressions) {
    }
}
