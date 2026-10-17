package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

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
